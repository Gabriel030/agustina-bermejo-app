package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.account.service.AbstractCurrentAccountIT;
import com.red_escuela.backend.billing.events.MonthlyInvoiceItemRetriesExhaustedEvent;
import com.red_escuela.backend.billing.events.MonthlyInvoiceRunMissedEvent;
import com.red_escuela.backend.billing.run.domain.FailedInvoiceRetrySchedule;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceEnrollmentType;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceItemOutcome;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunEvent;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItem;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;
import com.red_escuela.backend.billing.run.operation.MissedMonthlyInvoiceRunAlertJob;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunItemRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("D-055")
@Tag("D-160")
@Tag("D-459")
@RecordApplicationEvents
class MonthlyRunWatchdogAndAlertsIT extends AbstractCurrentAccountIT {
    private static final FailedInvoiceRetrySchedule NO_RETRIES = new FailedInvoiceRetrySchedule(0, List.of());

    @Autowired
    private MissedMonthlyInvoiceRunDetector detector;
    @Autowired
    private MissedMonthlyInvoiceRunAlertJob alertJob;
    @Autowired
    private BillingRunAlertPublisher alertPublisher;
    @Autowired
    private MonthlyInvoiceRunRepository runRepository;
    @Autowired
    private MonthlyInvoiceRunItemRepository itemRepository;
    @Autowired
    private ApplicationEvents events;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private EntityManager entityManager;

    private long missedAlertsOf(LocalDate firstDay) {
        return events.stream(MonthlyInvoiceRunMissedEvent.class).filter(event -> event.period().equals(firstDay)).count();
    }

    private Person staffWithRole(String role) {
        Person person = newStaffPerson();
        inTransaction(() -> entityManager.find(Person.class, person.getId()).getRoles().add(roleRepository.findByName(role).orElseThrow()));
        return person;
    }

    private long notificationsOf(Person person, NotificationType type, String textInMessage) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(person.getId()).stream()
                .filter(notification -> notification.getType() == type && notification.getMessage().contains(textInMessage)).count();
    }

    private MonthlyInvoiceRun seedRealRun(LocalDate firstDay, MonthlyInvoiceRunEvent finishingEvent) {
        MonthlyInvoiceRun run = MonthlyInvoiceRun.startRealDirectRun(firstDay, firstDay, MonthlyInvoiceRunTrigger.SCHEDULED, firstDay.atTime(4, 0));
        Optional.ofNullable(finishingEvent).ifPresent(event -> run.finishMonthlyRun(event, firstDay.atTime(4, 30)));
        return runRepository.saveAndFlush(run);
    }

    @Test
    void aPeriodWithoutAnyRunRaisesOneMissedAlertEvenIfTheWatchdogFiresOnSeveralDays() {
        LocalDate firstDay = LocalDate.of(2043, 3, 1);

        detector.alertWhenTheMonthlyRunOfTheCurrentPeriodDidNotRun(firstDay.plusDays(1));
        detector.alertWhenTheMonthlyRunOfTheCurrentPeriodDidNotRun(firstDay.plusDays(1));
        alertJob.run(firstDay.plusDays(2));
        alertJob.run(firstDay.plusDays(9));

        assertThat(missedAlertsOf(firstDay)).isEqualTo(1);
    }

    @Test
    void aCompletedRealRunOfThePeriodRaisesNoAlert() {
        LocalDate firstDay = LocalDate.of(2043, 4, 1);
        seedRealRun(firstDay, MonthlyInvoiceRunEvent.RUN_FINISHED_CLEAN);

        detector.alertWhenTheMonthlyRunOfTheCurrentPeriodDidNotRun(firstDay.plusDays(1));

        assertThat(missedAlertsOf(firstDay)).isZero();
    }

    @Test
    void aRunThatCompletedWithFailuresRaisesNoMissedAlertBecauseTheRetriesOwnThatCase() {
        LocalDate firstDay = LocalDate.of(2043, 5, 1);
        seedRealRun(firstDay, MonthlyInvoiceRunEvent.RUN_FINISHED_WITH_FAILURES);

        detector.alertWhenTheMonthlyRunOfTheCurrentPeriodDidNotRun(firstDay.plusDays(1));

        assertThat(missedAlertsOf(firstDay)).isZero();
    }

    @Test
    void aRunStillRunningRaisesNoAlert() {
        LocalDate firstDay = LocalDate.of(2043, 6, 1);
        seedRealRun(firstDay, null);

        detector.alertWhenTheMonthlyRunOfTheCurrentPeriodDidNotRun(firstDay);

        assertThat(missedAlertsOf(firstDay)).isZero();
    }

    @Test
    void aFailedRealRunLeavesThePeriodUnissuedAndRaisesTheMissedAlert() {
        LocalDate firstDay = LocalDate.of(2043, 7, 1);
        MonthlyInvoiceRun run = seedRealRun(firstDay, null);
        run.abortMonthlyRun("SWEEP_ABORTED", firstDay.atTime(4, 30));
        runRepository.saveAndFlush(run);

        detector.alertWhenTheMonthlyRunOfTheCurrentPeriodDidNotRun(firstDay.plusDays(1));

        assertThat(missedAlertsOf(firstDay)).isEqualTo(1);
    }

    @Test
    void aSimulationOfThePeriodDoesNotCountAsTheRealRun() {
        LocalDate firstDay = LocalDate.of(2043, 8, 1);
        runRepository.saveAndFlush(MonthlyInvoiceRun.startSimulationRun(firstDay, firstDay.minusDays(7), firstDay, MonthlyInvoiceRunTrigger.SCHEDULED, firstDay.minusDays(7).atTime(5, 0)));

        detector.alertWhenTheMonthlyRunOfTheCurrentPeriodDidNotRun(firstDay.plusDays(1));

        assertThat(missedAlertsOf(firstDay)).isEqualTo(1);
    }

    @Test
    void theMissedAlertReachesDirectorAndAdministrativeStaffOnceThroughTheSupervisionTopic() {
        LocalDate firstDay = LocalDate.of(2043, 9, 1);
        Person director = staffWithRole("DIRECTOR");
        Person administrative = staffWithRole("ADMINISTRATIVO");

        detector.alertWhenTheMonthlyRunOfTheCurrentPeriodDidNotRun(firstDay.plusDays(1));
        detector.alertWhenTheMonthlyRunOfTheCurrentPeriodDidNotRun(firstDay.plusDays(2));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(notificationsOf(director, NotificationType.MONTHLY_INVOICE_RUN_MISSED, "2043-09")).isEqualTo(1);
            assertThat(notificationsOf(administrative, NotificationType.MONTHLY_INVOICE_RUN_MISSED, "2043-09")).isEqualTo(1);
        });
    }

    @Test
    void anItemThatExhaustsItsRetriesRaisesOneAlertAndOneNotificationPerStaffMember() {
        LocalDate firstDay = LocalDate.of(2043, 10, 1);
        Person director = staffWithRole("DIRECTOR");
        MonthlyInvoiceRun run = seedRealRun(firstDay, MonthlyInvoiceRunEvent.RUN_FINISHED_WITH_FAILURES);
        MonthlyInvoiceRunItem item = itemRepository.saveAndFlush(MonthlyInvoiceRunItem.recordItemOutcome(run.getId(), MonthlyInvoiceEnrollmentType.PROGRAM, 4343L,
                MonthlyInvoiceItemOutcome.failed("PRICE_NOT_CONFIGURED", "sin precio"), LocalDateTime.of(2043, 10, 1, 4, 5), NO_RETRIES));
        assertThat(item.getResult()).isEqualTo(MonthlyInvoiceRunItemResult.RETRIES_EXHAUSTED);

        alertPublisher.publishItemRetriesExhaustedAlert(item);
        alertPublisher.publishItemRetriesExhaustedAlert(item);

        assertThat(events.stream(MonthlyInvoiceItemRetriesExhaustedEvent.class).filter(event -> event.monthlyInvoiceRunItemId().equals(item.getId()))).hasSize(1);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(notificationsOf(director, NotificationType.MONTHLY_INVOICE_ITEM_RETRIES_EXHAUSTED, "PRICE_NOT_CONFIGURED")).isEqualTo(1));
    }
}
