package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.account.service.AbstractCurrentAccountIT;
import com.red_escuela.backend.billing.collections.operation.HeldSubjectFinder;
import com.red_escuela.backend.billing.events.NightlyBillingRunFailedEvent;
import com.red_escuela.backend.billing.events.NightlyBillingRunMissedEvent;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.dto.BillingRunAlertResponse;
import com.red_escuela.backend.billing.run.dto.BillingRunAlertType;
import com.red_escuela.backend.billing.run.dto.NightlyBillingExecutionResponse;
import com.red_escuela.backend.billing.run.exception.InvalidBillingRunRangeException;
import com.red_escuela.backend.billing.run.operation.HeldAccountLookup;
import com.red_escuela.backend.billing.run.usecase.BillingRunAlertQueryUseCase;
import com.red_escuela.backend.billing.run.usecase.BillingRunExecutionUseCase;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@Tag("D-055")
@Tag("D-459")
@RecordApplicationEvents
class NightlyWatchdogAndContinuityIT extends AbstractCurrentAccountIT {

    @Autowired
    private MissedBillingRunDetector detector;
    @Autowired
    private BillingRunAlertQueryUseCase alertQuery;
    @Autowired
    private BillingRunExecutionUseCase executionUseCase;
    @Autowired
    private HeldAccountLookup heldAccounts;
    @Autowired
    private ApplicationEvents events;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private EntityManager entityManager;

    private Long seedNight(LocalDate day, String status) {
        return jdbc.queryForObject("insert into nightly_billing_run (business_date, status, trigger, started_at, finished_at) values (?, ?, 'SCHEDULED', now(), case when ? = 'RUNNING' then null else now() end) returning id",
                Long.class, day, status, status);
    }

    private Long seedStep(Long nightId, BillingRunStep step, String status) {
        return jdbc.queryForObject("insert into billing_run (nightly_billing_run_id, step, status, started_at, finished_at) values (?, ?, ?, now(), now()) returning id",
                Long.class, nightId, step.name(), status);
    }

    private void seedItem(Long stepRunId, String subjectType, Long subjectId, String status) {
        jdbc.update("insert into billing_run_item (billing_run_id, subject_type, subject_id, status, error_code) values (?, ?, ?, ?, 'X')", stepRunId, subjectType, subjectId, status);
    }

    private Person staffWithRole(String role) {
        Person person = newStaffPerson();
        inTransaction(() -> entityManager.find(Person.class, person.getId()).getRoles().add(roleRepository.findByName(role).orElseThrow()));
        return person;
    }

    private long countOfMissed(LocalDate day) {
        return events.stream(NightlyBillingRunMissedEvent.class).filter(event -> event.businessDate().equals(day)).count();
    }

    private long notificationsAbout(Person person, NotificationType type, LocalDate day) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(person.getId()).stream()
                .filter(notification -> notification.getType() == type && notification.getMessage().contains(day.toString())).count();
    }

    @Test
    void aMissingNightRaisesOneMissedAlertEvenIfTheWatchdogFiresTwice() {
        LocalDate day = LocalDate.of(2041, 1, 5);

        detector.alertWhenTheNightlyRunOfTodayIsNotCompleted(day);
        detector.alertWhenTheNightlyRunOfTodayIsNotCompleted(day);

        assertThat(countOfMissed(day)).isEqualTo(1);
    }

    @Test
    void aFailedNightRaisesTheFailedAlertAndNotTheMissedOne() {
        LocalDate day = LocalDate.of(2041, 1, 6);
        Long night = seedNight(day, "FAILED");
        seedStep(night, BillingRunStep.ACCOUNT_CONSISTENCY, "COMPLETED");
        seedStep(night, BillingRunStep.OVERDUE_DETECTION, "FAILED");

        detector.alertWhenTheNightlyRunOfTodayIsNotCompleted(day);
        detector.alertWhenTheNightlyRunOfTodayIsNotCompleted(day);

        assertThat(countOfMissed(day)).isZero();
        assertThat(events.stream(NightlyBillingRunFailedEvent.class).filter(event -> event.nightlyBillingRunId().equals(night)))
                .singleElement().satisfies(event -> {
                    assertThat(event.failedStep()).isEqualTo("OVERDUE_DETECTION");
                    assertThat(event.businessDate()).isEqualTo(day);
                });
    }

    @Test
    void aFailedNightWithoutAFailedStepRowReportsAnUnknownStep() {
        LocalDate day = LocalDate.of(2041, 1, 7);
        Long night = seedNight(day, "FAILED");

        detector.alertWhenTheNightlyRunOfTodayIsNotCompleted(day);

        assertThat(events.stream(NightlyBillingRunFailedEvent.class).filter(event -> event.nightlyBillingRunId().equals(night)))
                .singleElement().satisfies(event -> assertThat(event.failedStep()).isEqualTo("UNKNOWN"));
    }

    @Test
    void completedAndRunningNightsRaiseNoAlertFromTheWatchdog() {
        LocalDate completed = LocalDate.of(2041, 1, 8);
        LocalDate withErrors = LocalDate.of(2041, 1, 9);
        LocalDate running = LocalDate.of(2041, 1, 10);
        seedNight(completed, "COMPLETED");
        seedNight(withErrors, "COMPLETED_WITH_ERRORS");
        seedNight(running, "RUNNING");

        List.of(completed, withErrors, running).forEach(detector::alertWhenTheNightlyRunOfTodayIsNotCompleted);

        assertThat(events.stream(NightlyBillingRunMissedEvent.class).filter(event -> List.of(completed, withErrors, running).contains(event.businessDate()))).isEmpty();
        assertThat(events.stream(NightlyBillingRunFailedEvent.class).filter(event -> List.of(completed, withErrors, running).contains(event.businessDate()))).isEmpty();
    }

    @Test
    void theDirectorAndTheAdministrationGetOneMissedNotificationEach() {
        LocalDate day = LocalDate.of(2041, 1, 11);
        Person director = staffWithRole("DIRECTOR");
        Person administrative = staffWithRole("ADMINISTRATIVO");

        detector.alertWhenTheNightlyRunOfTodayIsNotCompleted(day);
        detector.alertWhenTheNightlyRunOfTodayIsNotCompleted(day);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(notificationsAbout(director, NotificationType.NIGHTLY_RUN_MISSED, day)).isEqualTo(1);
            assertThat(notificationsAbout(administrative, NotificationType.NIGHTLY_RUN_MISSED, day)).isEqualTo(1);
        });
    }

    @Test
    void anAccountWhoseInvoiceFailedInAnEarlierStepIsHeldInTheLaterStepsWithThePreviousStepFailedCode() {
        LocalDate day = LocalDate.of(2041, 1, 12);
        Long failingAccount = newAccountId(newStudentId(), Currency.ARS);
        Long failingInvoice = issueAdministrativeFeeWithLedgerDebit(failingAccount, "1000.00", day);
        Long siblingInvoice = issueAdministrativeFeeWithLedgerDebit(failingAccount, "2000.00", day);
        Long healthyAccount = newAccountId(newStudentId(), Currency.ARS);
        Long healthyInvoice = issueAdministrativeFeeWithLedgerDebit(healthyAccount, "3000.00", day);
        Long night = seedNight(day, "RUNNING");
        seedItem(seedStep(night, BillingRunStep.OVERDUE_DETECTION, "COMPLETED_WITH_ERRORS"), "INVOICE", failingInvoice, "FAILED");

        Map<Long, String> held = heldAccounts.heldInvoicesOf(day).findHoldCodesAmong(List.of(failingInvoice, siblingInvoice, healthyInvoice));

        assertThat(held).containsOnlyKeys(failingInvoice, siblingInvoice);
        assertThat(held.values()).containsOnly(HeldSubjectFinder.PREVIOUS_STEP_FAILED_CODE);
        assertThat(heldAccounts.findHeldAccountCodes(day)).containsOnly(Map.entry(failingAccount, HeldSubjectFinder.PREVIOUS_STEP_FAILED_CODE));
    }

    @Test
    void aSkippedItemOfAnEarlierStepDoesNotHoldTheAccount() {
        LocalDate day = LocalDate.of(2041, 1, 13);
        Long account = newAccountId(newStudentId(), Currency.ARS);
        Long invoice = issueAdministrativeFeeWithLedgerDebit(account, "1000.00", day);
        Long night = seedNight(day, "RUNNING");
        seedItem(seedStep(night, BillingRunStep.OVERDUE_DETECTION, "COMPLETED_WITH_ERRORS"), "INVOICE", invoice, "SKIPPED");

        assertThat(heldAccounts.heldInvoicesOf(day).findHoldCodesAmong(List.of(invoice))).isEmpty();
        assertThat(heldAccounts.findHeldAccountCodes(day)).doesNotContainKey(account);
    }

    @Test
    void anInconsistentAccountKeepsTheInconsistencyCodeEvenIfItAlsoFailedLater() {
        LocalDate day = LocalDate.of(2041, 1, 14);
        Long account = newAccountId(newStudentId(), Currency.ARS);
        Long invoice = issueAdministrativeFeeWithLedgerDebit(account, "1000.00", day);
        Long night = seedNight(day, "RUNNING");
        seedItem(seedStep(night, BillingRunStep.ACCOUNT_CONSISTENCY, "COMPLETED_WITH_ERRORS"), "CURRENT_ACCOUNT", account, "SKIPPED");
        seedItem(seedStep(night, BillingRunStep.OVERDUE_DETECTION, "COMPLETED_WITH_ERRORS"), "INVOICE", invoice, "FAILED");

        assertThat(heldAccounts.heldInvoicesOf(day).findHoldCodesAmong(List.of(invoice))).containsOnly(Map.entry(invoice, HeldSubjectFinder.INCONSISTENT_ACCOUNT_CODE));
        assertThat(heldAccounts.findHeldAccountCodes(day)).containsEntry(account, HeldSubjectFinder.INCONSISTENT_ACCOUNT_CODE);
    }

    @Test
    void theAlertsListFailedAndWithErrorsNightsAndTheMissedDaysOnlyOnceTheWatchdogHasLooked() {
        seedNight(LocalDate.of(2041, 2, 1), "FAILED");
        seedNight(LocalDate.of(2041, 2, 2), "COMPLETED_WITH_ERRORS");
        seedNight(LocalDate.of(2041, 2, 3), "COMPLETED");
        MutableClock.INSTANCE.setDateTime(LocalDate.of(2041, 2, 5).atTime(5, 59));

        List<BillingRunAlertResponse> beforeTheWatchdog = alertQuery.listBillingRunAlerts(LocalDate.of(2041, 2, 1), LocalDate.of(2041, 2, 5));
        MutableClock.INSTANCE.setDateTime(LocalDate.of(2041, 2, 5).atTime(6, 0));
        List<BillingRunAlertResponse> afterTheWatchdog = alertQuery.listBillingRunAlerts(LocalDate.of(2041, 2, 1), LocalDate.of(2041, 2, 5));

        assertThat(beforeTheWatchdog).extracting(BillingRunAlertResponse::type, BillingRunAlertResponse::businessDate).containsExactly(
                org.assertj.core.groups.Tuple.tuple(BillingRunAlertType.NIGHTLY_RUN_MISSED, LocalDate.of(2041, 2, 4)),
                org.assertj.core.groups.Tuple.tuple(BillingRunAlertType.NIGHTLY_RUN_COMPLETED_WITH_ERRORS, LocalDate.of(2041, 2, 2)),
                org.assertj.core.groups.Tuple.tuple(BillingRunAlertType.NIGHTLY_RUN_FAILED, LocalDate.of(2041, 2, 1)));
        assertThat(afterTheWatchdog).extracting(BillingRunAlertResponse::businessDate).startsWith(LocalDate.of(2041, 2, 5)).hasSize(4);
        assertThat(afterTheWatchdog.get(0).nightlyRunId()).isNull();
        assertThat(afterTheWatchdog.get(0).message()).isEqualTo("El proceso nocturno no corrió");
    }

    @Test
    void anInvertedAlertRangeIsRejected() {
        assertThatThrownBy(() -> alertQuery.listBillingRunAlerts(LocalDate.of(2041, 2, 5), LocalDate.of(2041, 2, 1))).isInstanceOf(InvalidBillingRunRangeException.class);
    }

    @Test
    @Tag("D-454")
    void theManualExecutionRunsTodaysNightOnceAndTheSecondCallReturnsTheSameRun() {
        MutableClock.INSTANCE.setDateTime(LocalDate.of(2041, 3, 1).atTime(9, 0));

        NightlyBillingExecutionResponse first = executionUseCase.executeTodaysNightlyBillingRunUnlessItExists();
        NightlyBillingExecutionResponse second = executionUseCase.executeTodaysNightlyBillingRunUnlessItExists();

        assertThat(first.started()).isTrue();
        assertThat(first.run().businessDate()).isEqualTo(LocalDate.of(2041, 3, 1));
        assertThat(first.run().trigger().name()).isEqualTo("MANUAL");
        assertThat(second.started()).isFalse();
        assertThat(second.run().id()).isEqualTo(first.run().id());
    }
}
