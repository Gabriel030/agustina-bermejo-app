package com.red_escuela.backend.shared.retention;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.specialist.IdentityDataRetentionJob;
import com.red_escuela.backend.notification.specialist.NotificationDataRetentionJob;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class DataRetentionIT extends AbstractIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 6, 15);
    private static final LocalDateTime NOW = LocalDateTime.of(TODAY, LocalTime.of(3, 40));

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private LockProvider lockProvider;
    @Autowired
    private IdentityDataRetentionJob scheduledIdentityJob;
    @Autowired
    private NotificationDataRetentionJob scheduledNotificationJob;

    private SimpleMeterRegistry meters;
    private IdentityDataRetentionJob identityJob;
    private NotificationDataRetentionJob notificationJob;
    private Long personId;

    @BeforeEach
    void freezeClockAndBuildJobsWithTinyBatches() {
        MutableClock.INSTANCE.setDateTime(NOW);
        meters = new SimpleMeterRegistry();
        RetentionSettings settings = new RetentionSettings(true, 12, 12, 12, 36, 2, 100);
        RetentionBatchPurger purger = new RetentionBatchPurger(namedJdbc, transactionManager, meters, settings);
        identityJob = new IdentityDataRetentionJob(purger, settings, MutableClock.INSTANCE);
        notificationJob = new NotificationDataRetentionJob(purger, settings, MutableClock.INSTANCE);
        personId = persistPerson();
    }

    private Long persistPerson() {
        String dni = "8" + System.nanoTime();
        return transactionTemplate.execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            person.setName("Persona");
            person.setLastname(dni);
            person.setDni(dni);
            person.setUsername(dni);
            person.setEmail(dni + "@example.com");
            person.setEmailVerified(true);
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            entityManager.persist(person);
            return person.getId();
        });
    }

    private long insertLoginAttempt(LocalDateTime attemptedAt) {
        return jdbc.queryForObject("insert into login_attempt (person_id, attempted_username, successful, attempted_at) values (?, 'x', true, ?) returning id",
                Long.class, personId, attemptedAt);
    }

    private long insertRefreshToken(LocalDateTime expiresAt, Long parentId) {
        return jdbc.queryForObject("insert into refresh_token (person_id, token_hash, expires_at, revoked, created_at, parent_id) values (?, ?, ?, false, ?, ?) returning id",
                Long.class, personId, "h" + System.nanoTime(), expiresAt, NOW.minusDays(30), parentId);
    }

    private long insertNotification(LocalDateTime createdAt, boolean read, Long relatedEntityId) {
        return jdbc.queryForObject("insert into notification (recipient_id, type, message, read, related_entity_type, related_entity_id, created_at) "
                        + "values (?, 'COLLECTION_REMINDER', 'm', ?, ?, ?, ?) returning id",
                Long.class, personId, read, relatedEntityId == null ? null : "Invoice", relatedEntityId, createdAt);
    }

    private long insertDelivery(String category, String status, LocalDate scheduledFor) {
        String reason = status.equals("QUEUED") || status.equals("SENT") ? null : "REASON";
        return jdbc.queryForObject("insert into notification_delivery (dedup_key, channel, category, kind, priority, recipient_person_id, status, status_reason, "
                        + "scheduled_for, body_text, sent_at, created_at) values (?, 'IN_APP', ?, 'K', 1, ?, ?, ?, ?, 'b', ?, now()) returning id",
                Long.class, "r:" + System.nanoTime(), category, personId, status, reason, scheduledFor,
                status.equals("SENT") ? java.sql.Timestamp.valueOf(scheduledFor.atStartOfDay()) : null);
    }

    private boolean exists(String table, long id) {
        return jdbc.queryForObject("select count(*) from " + table + " where id = ?", Integer.class, id) == 1;
    }

    @Test
    void loginAttemptsOlderThanTwelveMonthsAreDeletedInSeveralBatchesAndRecentOnesStay() {
        long[] old = new long[5];
        for (int i = 0; i < old.length; i++) {
            old[i] = insertLoginAttempt(NOW.minusMonths(12).minusDays(1 + i));
        }
        long recent = insertLoginAttempt(NOW.minusMonths(12).plusDays(1));

        identityJob.deleteExpiredIdentityData();

        for (long id : old) {
            assertThat(exists("login_attempt", id)).isFalse();
        }
        assertThat(exists("login_attempt", recent)).isTrue();
        assertThat(meters.counter(RetentionBatchPurger.METER_NAME, "table", "login_attempt").count()).isGreaterThanOrEqualTo(5.0);
    }

    @Test
    void expiredTokensAreDeletedAndAValidRotatedChildSurvivesItsDeletedParent() {
        long expiredParent = insertRefreshToken(NOW.minusMinutes(1), null);
        long validChild = insertRefreshToken(NOW.plusDays(5), expiredParent);
        long validAlone = insertRefreshToken(NOW.plusMinutes(1), null);
        jdbc.update("insert into password_reset_token (person_id, token_hash, expires_at, used, created_at) values (?, ?, ?, false, ?)",
                personId, "p" + System.nanoTime(), NOW.minusMinutes(1), NOW.minusDays(1));
        jdbc.update("insert into email_verification_token (person_id, token_hash, expires_at) values (?, ?, ?)",
                personId, "e" + System.nanoTime(), NOW.minusMinutes(1));

        identityJob.deleteExpiredIdentityData();

        assertThat(exists("refresh_token", expiredParent)).isFalse();
        assertThat(exists("refresh_token", validChild)).isTrue();
        assertThat(exists("refresh_token", validAlone)).isTrue();
        assertThat(jdbc.queryForObject("select parent_id from refresh_token where id = ?", Long.class, validChild)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from password_reset_token where person_id = ?", Integer.class, personId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from email_verification_token where person_id = ?", Integer.class, personId)).isZero();
    }

    @Test
    void oldTerminalDeliveriesAreDeletedButQueuedDeferredAndRecentOnesStay() {
        LocalDate old = TODAY.minusMonths(12).minusDays(1);
        long oldSent = insertDelivery("ANNOUNCEMENT", "SENT", old);
        long oldSuppressed = insertDelivery("ANNOUNCEMENT", "SUPPRESSED", old);
        long oldQueued = insertDelivery("ANNOUNCEMENT", "QUEUED", old);
        long oldDeferred = insertDelivery("ANNOUNCEMENT", "DEFERRED", old);
        long recentSent = insertDelivery("ANNOUNCEMENT", "SENT", TODAY.minusMonths(12).plusDays(1));

        notificationJob.deleteExpiredNotificationData();

        assertThat(exists("notification_delivery", oldSent)).isFalse();
        assertThat(exists("notification_delivery", oldSuppressed)).isFalse();
        assertThat(exists("notification_delivery", oldQueued)).isTrue();
        assertThat(exists("notification_delivery", oldDeferred)).isTrue();
        assertThat(exists("notification_delivery", recentSent)).isTrue();
    }

    @Test
    void collectionReminderDeliveriesAreKeptForThirtySixMonthsSoTheMonthlyMetricKeepsItsWholeWindow() {
        long fourteenMonthsOld = insertDelivery("COLLECTION_REMINDER", "SENT", TODAY.minusMonths(14));
        long thirtySevenMonthsOld = insertDelivery("COLLECTION_REMINDER", "SENT", TODAY.minusMonths(37));

        notificationJob.deleteExpiredNotificationData();

        assertThat(exists("notification_delivery", fourteenMonthsOld)).isTrue();
        assertThat(exists("notification_delivery", thirtySevenMonthsOld)).isFalse();
    }

    @Test
    void oldNotificationsAreDeletedExceptUnreadOnesWithAPendingRelatedProcedure() {
        LocalDateTime old = NOW.minusMonths(12).minusDays(1);
        long oldRead = insertNotification(old, true, 7L);
        long oldUnreadWithoutProcedure = insertNotification(old, false, null);
        long oldUnreadPending = insertNotification(old, false, 7L);
        long recentRead = insertNotification(NOW.minusMonths(12).plusDays(1), true, null);

        notificationJob.deleteExpiredNotificationData();

        assertThat(exists("notification", oldRead)).isFalse();
        assertThat(exists("notification", oldUnreadWithoutProcedure)).isFalse();
        assertThat(exists("notification", oldUnreadPending)).isTrue();
        assertThat(exists("notification", recentRead)).isTrue();
    }

    @Test
    void aDisabledRetentionDeletesNothing() {
        long oldAttempt = insertLoginAttempt(NOW.minusMonths(30));
        long oldNotification = insertNotification(NOW.minusMonths(30), true, null);
        RetentionSettings disabled = new RetentionSettings(false, 12, 12, 12, 36, 2, 100);
        RetentionBatchPurger purger = new RetentionBatchPurger(namedJdbc, transactionManager, meters, disabled);

        int deleted = new IdentityDataRetentionJob(purger, disabled, MutableClock.INSTANCE).deleteExpiredIdentityData()
                + new NotificationDataRetentionJob(purger, disabled, MutableClock.INSTANCE).deleteExpiredNotificationData();

        assertThat(deleted).isZero();
        assertThat(exists("login_attempt", oldAttempt)).isTrue();
        assertThat(exists("notification", oldNotification)).isTrue();
    }

    @Test
    void runningTheJobsTwiceDeletesOnceAndTheSecondRunFindsNothing() {
        insertLoginAttempt(NOW.minusMonths(20));
        insertNotification(NOW.minusMonths(20), true, null);
        identityJob.deleteExpiredIdentityData();
        notificationJob.deleteExpiredNotificationData();

        assertThat(identityJob.deleteExpiredIdentityData()).isZero();
        assertThat(notificationJob.deleteExpiredNotificationData()).isZero();
    }

    @Test
    void theRetentionJobsAreSkippedWhileAnotherInstanceHoldsTheirLock() {
        long oldAttempt = insertLoginAttempt(NOW.minusMonths(20));
        long oldNotification = insertNotification(NOW.minusMonths(20), true, null);
        Optional<SimpleLock> identityLock = lockProvider.lock(new LockConfiguration(Instant.now(), "IdentityDataRetentionJob", Duration.ofMinutes(5), Duration.ZERO));
        Optional<SimpleLock> notificationLock = lockProvider.lock(new LockConfiguration(Instant.now(), "NotificationDataRetentionJob", Duration.ofMinutes(5), Duration.ZERO));
        assertThat(identityLock).isPresent();
        assertThat(notificationLock).isPresent();
        try {
            scheduledIdentityJob.run();
            scheduledNotificationJob.run();
            assertThat(exists("login_attempt", oldAttempt)).isTrue();
            assertThat(exists("notification", oldNotification)).isTrue();
        } finally {
            identityLock.get().unlock();
            notificationLock.get().unlock();
        }

        scheduledIdentityJob.run();
        scheduledNotificationJob.run();

        assertThat(exists("login_attempt", oldAttempt)).isFalse();
        assertThat(exists("notification", oldNotification)).isFalse();
    }

    @Test
    void aRetentionPeriodBelowOneMonthIsRejected() {
        assertThatThrownBy(() -> new RetentionSettings(true, 0, 12, 12, 36, 1000, 1000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetentionSettings(true, 12, 12, 12, 36, 0, 1000)).isInstanceOf(IllegalArgumentException.class);
    }
}
