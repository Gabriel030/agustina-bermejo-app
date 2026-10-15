package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentService;
import com.red_escuela.backend.enrollment.service.EnrollmentWithdrawal;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

import java.sql.SQLException;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-375")
class LateFeeCorrectionIntegrityIT extends AbstractLateFeeScenarioIT {

    @Autowired
    private CourseEnrollmentService courseEnrollmentService;
    @Autowired
    private LateFeeWithdrawalCorrectionService correctionService;
    @Autowired
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @AfterEach
    void resetClock() {
        MutableClock.INSTANCE.reset();
    }

    private Long correctedDebitNote(int year) {
        arrange(year);
        publishDefaults(year);
        Invoice origin = overdueInstallmentDueOn(d(year, 4, 11));
        reconcile(origin, d(year, 5, 1));
        Long debitNoteId = (Long) chargesOf(origin).get(0).get("debit_note_id");
        MutableClock.INSTANCE.setDate(d(year, 5, 3));
        courseEnrollmentService.withdraw(enrollment.getId(), EnrollmentWithdrawal.voluntaryRegisteredBy(d(year, 4, 28), person.getId()));
        awaitEventsDrained();
        return debitNoteId;
    }

    private List<String> violationsAfter(String... statements) {
        return transactionTemplate.execute(status -> {
            jdbc.execute("set local session_replication_role = replica");
            for (String statement : statements) {
                jdbc.execute(statement);
            }
            List<String> found = invariants.lateFeeCorrectionsAreConsistent();
            status.setRollbackOnly();
            return found;
        });
    }

    @Test
    void correctingTheSameWithdrawalAgainCreditsNothingMore() {
        Long debitNoteId = correctedDebitNote(2134);

        int correctedAgain = correctionService.correctLateFeesOfEnrollment(null, enrollment.getId(), d(2134, 5, 3));

        assertThat(correctedAgain).isZero();
        assertThat(jdbc.queryForObject("select count(*) from credit_note where invoice_id = ?", Long.class, debitNoteId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from late_fee_charge_correction where debit_note_id = ?", Long.class, debitNoteId)).isEqualTo(1L);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void aDebitNoteThatAlreadyHasAManualCreditIsCreditedOnlyUpToWhatItAdmitsAndTheCapIsCounted() {
        arrange(2135);
        publishDefaults(2135);
        Invoice origin = overdueInstallmentDueOn(d(2135, 4, 11));
        reconcile(origin, d(2135, 5, 1));
        Long debitNoteId = (Long) chargesOf(origin).get(0).get("debit_note_id");
        jdbc.update("update invoice_line set credited_amount = 3700.00 where invoice_id = ?", debitNoteId);
        double cappedBefore = meterRegistry.counter("billing_late_fee_corrections_capped_total", "currency", "ARS").count();
        MutableClock.INSTANCE.setDate(d(2135, 5, 3));

        courseEnrollmentService.withdraw(enrollment.getId(), EnrollmentWithdrawal.voluntaryRegisteredBy(d(2135, 4, 28), person.getId()));
        awaitEventsDrained();

        assertThat(jdbc.queryForObject("select total_amount from credit_note where invoice_id = ?", java.math.BigDecimal.class, debitNoteId)).isEqualByComparingTo("100.00");
        assertThat(jdbc.queryForObject("select corrected_days from late_fee_charge_correction where debit_note_id = ?", Integer.class, debitNoteId)).isEqualTo(2);
        assertThat(meterRegistry.counter("billing_late_fee_corrections_capped_total", "currency", "ARS").count()).isGreaterThan(cappedBefore);
        assertThat(invariants.lateFeeCorrectionsAreConsistent()).isEmpty();
    }

    @Test
    void aDebitNoteIssuedWithDaysAfterAnAlreadyRegisteredWithdrawalIsNotMaskedByASecondCorrectionRun() {
        arrange(2136);
        publish(0, "0.002", 10, false, true, d(2136, 1, 1));
        Invoice origin = overdueInstallmentDueOn(d(2136, 4, 11));
        MutableClock.INSTANCE.setDate(d(2136, 5, 3));
        courseEnrollmentService.withdraw(enrollment.getId(), EnrollmentWithdrawal.voluntaryRegisteredBy(d(2136, 4, 28), person.getId()));
        awaitEventsDrained();
        reconcile(origin, d(2136, 5, 4));
        publish(0, "0.002", 10, true, true, d(2136, 4, 28));
        assertThat(summaryOf(origin)).containsExactly("2136-04-12..2136-04-30:19:3800.00");

        int corrected = correctionService.correctLateFeesOfEnrollment(null, enrollment.getId(), d(2136, 5, 4));

        assertThat(corrected).isEqualTo(1);
        assertThat(invariants.lateFeeCorrectionsAreConsistent()).hasSize(1).allMatch(v -> v.startsWith("I-11i"));
    }

    @Test
    void theCorrectedDaysColumnAcceptsAWholeThirtyOneDayMonthAndNothingMore() {
        String insert = "insert into late_fee_charge_correction (late_fee_charge_id, debit_note_id, student_current_account_id, credit_note_id, first_corrected_day, "
                + "last_corrected_day, corrected_days, cause, enrollment_end_date, created_at) values (1, 1, 1, 999999, '2140-03-01', '2140-03-31', %d, 'BACKDATED_WITHDRAWAL', '2140-02-28', now())";

        transactionTemplate.executeWithoutResult(status -> {
            jdbc.execute("set local session_replication_role = replica");
            assertThat(jdbc.update(insert.formatted(31))).isEqualTo(1);
            status.setRollbackOnly();
        });
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbc.execute("set local session_replication_role = replica");
            jdbc.update(insert.formatted(32));
        })).isInstanceOf(DataAccessException.class);
    }

    @Test
    void anOverlappingCorrectionOfTheSameChargeIsRejectedByTheDatabase() {
        Long debitNoteId = correctedDebitNote(2130);
        Map<String, Object> existing = jdbc.queryForMap("select * from late_fee_charge_correction where debit_note_id = ?", debitNoteId);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbc.execute("set local session_replication_role = replica");
            jdbc.update("insert into late_fee_charge_correction (late_fee_charge_id, debit_note_id, student_current_account_id, credit_note_id, first_corrected_day, "
                            + "last_corrected_day, corrected_days, cause, enrollment_end_date, created_at) values (?, ?, ?, ?, '2130-04-30', '2130-04-30', 1, 'BACKDATED_WITHDRAWAL', "
                            + "'2130-04-28', now())", existing.get("late_fee_charge_id"), debitNoteId, existing.get("student_current_account_id"),
                    ((Long) existing.get("credit_note_id")) + 1_000_000L);
        })).isInstanceOf(DataAccessException.class).satisfies(failure -> {
            SQLException sql = (SQLException) rootOf(failure);
            assertThat(sql.getSQLState()).isEqualTo("23P01");
            assertThat(sql.getMessage()).contains("ex_late_fee_charge_correction_no_overlapping_days");
        });
    }

    @Test
    void aCorrectionCannotBeUpdatedNorDeleted() {
        Long debitNoteId = correctedDebitNote(2131);

        assertThatThrownBy(() -> jdbc.update("update late_fee_charge_correction set corrected_days = 1 where debit_note_id = ?", debitNoteId)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from late_fee_charge_correction where debit_note_id = ?", debitNoteId)).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("select count(*) from late_fee_charge_correction where debit_note_id = ?", Long.class, debitNoteId)).isEqualTo(1L);
    }

    @Test
    void theCheckerFlagsEachInconsistentCorrectionAndAcceptsTheCleanScenario() {
        Long debitNoteId = correctedDebitNote(2132);
        assertThat(invariants.lateFeeCorrectionsAreConsistent()).isEmpty();
        invariants.assertAllBillingInvariantsHold();

        assertThat(violationsAfter("alter table late_fee_charge_correction drop constraint ck_late_fee_charge_correction_one_month",
                "update late_fee_charge_correction set last_corrected_day = '2132-05-02' where debit_note_id = " + debitNoteId))
                .hasSize(1).allMatch(v -> v.startsWith("I-11i"));
        assertThat(violationsAfter("update course_enrollment set completion_date = '2132-04-29 10:00:00' where id = " + enrollment.getId()))
                .hasSize(1).allMatch(v -> v.startsWith("I-11i"));
        assertThat(violationsAfter("update credit_note set total_amount = total_amount + 1, offset_amount = offset_amount + 1 where invoice_id = " + debitNoteId))
                .hasSize(1).allMatch(v -> v.startsWith("I-11i"));
        assertThat(violationsAfter("update credit_note set recipient_snapshot = '{\"name\":\"Otra persona\"}'::jsonb where invoice_id = " + debitNoteId))
                .hasSize(1).allMatch(v -> v.startsWith("I-11i"));
        assertThat(violationsAfter("update credit_note set reason = 'BILLING_ERROR' where invoice_id = " + debitNoteId))
                .hasSize(1).allMatch(v -> v.startsWith("I-11i"));
        assertThat(violationsAfter("update credit_note set total_amount = total_amount - 1, offset_amount = offset_amount - 1 where invoice_id = " + debitNoteId))
                .hasSize(1).allMatch(v -> v.startsWith("I-11i"));
        assertThat(violationsAfter("update invoice set issue_date = '2132-05-04' where id = " + debitNoteId))
                .hasSize(1).allMatch(v -> v.startsWith("I-11i"));
        assertThat(violationsAfter("update course_enrollment set status = 'ENROLLED', completion_date = null, withdrawal_effective_date = null, withdrawal_registered_on = null where id = " + enrollment.getId()))
                .hasSize(1).allMatch(v -> v.startsWith("I-11i"));
        assertThat(invariants.lateFeeCorrectionsAreConsistent()).isEmpty();
    }

    @Test
    @Tag("D-153")
    void aDebitNoteUnderPaymentReviewIsNotCorrectedWhileTheOtherOnesOfTheSameWithdrawalAre() {
        arrange(2133);
        publishDefaults(2133);
        Invoice reviewedOrigin = overdueInstallmentDueOn(d(2133, 4, 11));
        Invoice otherOrigin = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.of(2133, 3)).dueOn(d(2133, 3, 11)).withBaseAmount(ars(100000)).overdue().build();
        reconcile(reviewedOrigin, d(2133, 5, 1));
        reconcile(otherOrigin, d(2133, 5, 1));
        Long reviewedNote = (Long) chargesOf(reviewedOrigin).get(0).get("debit_note_id");
        Long otherNote = (Long) chargesOf(otherOrigin).get(1).get("debit_note_id");
        transactionTemplate.executeWithoutResult(status -> invoiceRepository.findByIdForUpdate(reviewedNote).orElseThrow()
                .markPaymentUnderReview(d(2133, 5, 2), ACTOR, "Comprobante"));

        MutableClock.INSTANCE.setDate(d(2133, 5, 3));
        courseEnrollmentService.withdraw(enrollment.getId(), EnrollmentWithdrawal.voluntaryRegisteredBy(d(2133, 4, 28), person.getId()));
        awaitEventsDrained();

        assertThat(jdbc.queryForObject("select count(*) from credit_note where invoice_id = ?", Long.class, reviewedNote)).isZero();
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, reviewedNote)).isEqualTo("PAYMENT_UNDER_REVIEW");
        assertThat(jdbc.queryForObject("select count(*) from credit_note where invoice_id = ?", Long.class, otherNote)).isEqualTo(1L);
    }

    private static Throwable rootOf(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
