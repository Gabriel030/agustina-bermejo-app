package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentService;
import com.red_escuela.backend.enrollment.service.EnrollmentWithdrawal;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-378")
@Tag("D-331")
@SlowTest(reason = "Shares the heavy Spring context of the late fee scenario hierarchy: when it is the first class of its shard it absorbs the whole context start-up")
class LateFeePaymentDateInvariantIT extends AbstractLateFeeScenarioIT {

    @Autowired
    private CourseEnrollmentService courseEnrollmentService;

    @AfterEach
    void resetClock() {
        MutableClock.INSTANCE.reset();
    }

    private Invoice originWithAWithdrawalAndAPaymentCorrection(int year) {
        arrange(year);
        publishDefaults(year);
        Invoice origin = overdueInstallmentDueOn(d(year, 3, 11));
        reconcile(origin, d(year, 4, 2));
        MutableClock.INSTANCE.setDate(d(year, 4, 3));
        courseEnrollmentService.withdraw(enrollment.getId(), EnrollmentWithdrawal.voluntaryRegisteredBy(d(year, 3, 25), person.getId()));
        awaitEventsDrained();
        MutableClock.INSTANCE.setDate(d(year, 4, 4));
        payInFull(origin, d(year, 3, 20), d(year, 4, 4));
        awaitEventsDrained();
        return origin;
    }

    private List<String> violationsAfter(String prefix, String... statements) {
        return transactionTemplate.execute(status -> {
            jdbc.execute("set local session_replication_role = replica");
            for (String statement : statements) {
                jdbc.execute(statement);
            }
            List<String> found = invariants.lateFeeCorrectionsAreConsistent().stream().filter(violation -> violation.startsWith(prefix)).toList();
            List<String> daysFound = invariants.lateFeesRespectTheRedefinedI11().stream().filter(violation -> violation.startsWith(prefix)).toList();
            status.setRollbackOnly();
            return prefix.equals("I-11c") ? daysFound : found;
        });
    }

    @Test
    void invariantCheckerDetectsEachPaymentCorrectionViolation() {
        Invoice origin = originWithAWithdrawalAndAPaymentCorrection(2409);
        Long debitNoteId = (Long) chargesOf(origin).get(0).get("debit_note_id");
        String paymentCorrection = "select id from late_fee_charge_correction where debit_note_id = " + debitNoteId + " and cause <> 'BACKDATED_WITHDRAWAL'";
        String paymentNote = "select credit_note_id from late_fee_charge_correction where debit_note_id = " + debitNoteId + " and cause <> 'BACKDATED_WITHDRAWAL'";
        Long settlementAllocationId = jdbc.queryForObject("select settlement_allocation_id from late_fee_charge_correction where debit_note_id = ? and cause <> 'BACKDATED_WITHDRAWAL'",
                Long.class, debitNoteId);

        assertThat(invariants.lateFeeCorrectionsAreConsistent()).isEmpty();
        invariants.assertAllBillingInvariantsHold();

        assertThat(violationsAfter("I-11j", "alter table late_fee_charge_correction drop constraint ck_late_fee_charge_correction_after_payment",
                "update late_fee_charge_correction set payment_date = first_corrected_day where id = (" + paymentCorrection + ")")).hasSize(1);
        assertThat(violationsAfter("I-11j", "update credit_note set total_amount = total_amount + 1, offset_amount = offset_amount + 1 where id = (" + paymentNote + ")")).hasSize(1);
        assertThat(violationsAfter("I-11j", "update credit_note set total_amount = total_amount - 1, offset_amount = offset_amount - 1 where id = (" + paymentNote + ")")).hasSize(1);
        assertThat(violationsAfter("I-11j", "update credit_note set reason = 'LATE_FEE_CORRECTION' where id = (" + paymentNote + ")")).hasSize(1);
        assertThat(violationsAfter("I-11j", "update credit_note set recipient_snapshot = '{\"name\":\"Otra persona\"}'::jsonb where id = (" + paymentNote + ")")).hasSize(1);
        assertThat(violationsAfter("I-11j", "update payment_allocation set invoice_id = " + debitNoteId + " where id = " + settlementAllocationId)).hasSize(1);
        assertThat(violationsAfter("I-11j", "update payment_allocation set effective_date = effective_date - 1 where id = " + settlementAllocationId)).hasSize(1);
        assertThat(violationsAfter("I-11j", "update late_fee_charge_correction set cause = 'PAYMENT_WITHIN_GRACE' where id = (" + paymentCorrection + ")")).hasSize(1);
        assertThat(violationsAfter("I-11i", "update credit_note set reason = 'LATE_FEE_CORRECTION' where id = (" + paymentNote + ")")).isEmpty();
        assertThat(violationsAfter("I-11c", "delete from late_fee_charge_correction where id = (" + paymentCorrection + ")")).hasSize(1);
        assertThat(invariants.lateFeeCorrectionsAreConsistent()).isEmpty();
    }

    private <T> T afterInRollback(Supplier<T> reading, String... statements) {
        return transactionTemplate.execute(status -> {
            jdbc.execute("set local session_replication_role = replica");
            for (String statement : statements) {
                jdbc.execute(statement);
            }
            T read = reading.get();
            status.setRollbackOnly();
            return read;
        });
    }

    @Test
    void invariantCheckerDetectsEachDailyCoverageViolationOfTheChargedDays() {
        int year = 2410;
        Invoice origin = originWithAWithdrawalAndAPaymentCorrection(year);
        Long debitNoteId = (Long) chargesOf(origin).get(0).get("debit_note_id");
        Long chargeId = (Long) chargesOf(origin).get(0).get("id");
        Long versionId = (Long) chargesOf(origin).get(0).get("late_payment_version_id");
        LocalDate today = d(year, 4, 4);
        String own = "debit note " + debitNoteId;
        String corrections = "late_fee_charge_correction where late_fee_charge_id = " + chargeId;

        assertThat(invariants.lateFeesRespectTheRedefinedI11()).isEmpty();
        assertThat(invariants.lateFeesCoverEveryChargeableDayAt(today)).isEmpty();

        assertThat(afterInRollback(invariants::lateFeesRespectTheRedefinedI11, "alter table late_fee_charge drop constraint ck_late_fee_charge_charged_days",
                "update late_fee_charge set charged_days = charged_days + 1 where id = " + chargeId))
                .anyMatch(v -> v.startsWith("I-11c charged days") && v.contains(own));
        assertThat(afterInRollback(invariants::lateFeesRespectTheRedefinedI11, "alter table late_fee_charge drop constraint ck_late_fee_charge_charged_days",
                "update late_fee_charge set charged_days = charged_days - 1 where id = " + chargeId))
                .anyMatch(v -> v.startsWith("I-11c charged days") && v.contains(own));
        assertThat(afterInRollback(invariants::lateFeesRespectTheRedefinedI11,
                "update configuration_version set config_values = jsonb_set(config_values, '{lateFeeChargingEnabled}', 'false') where id = " + versionId))
                .anyMatch(v -> v.startsWith("I-11c charged days") && v.contains(own));
        assertThat(afterInRollback(invariants::lateFeesRespectTheRedefinedI11, "delete from " + corrections))
                .anyMatch(v -> v.startsWith("I-11c charged days") && v.contains(own))
                .anyMatch(v -> v.startsWith("I-11e late fee charged") && v.contains(own));
        assertThat(afterInRollback(invariants::lateFeesRespectTheRedefinedI11,
                "update payment_allocation set effective_date = effective_date + 30 where invoice_id = " + origin.getId() + " and reverses_allocation_id is null"))
                .anyMatch(v -> v.startsWith("I-11c charged days") && v.contains(own));
        assertThat(afterInRollback(() -> invariants.lateFeesCoverEveryChargeableDayAt(today), "delete from " + corrections,
                "delete from late_fee_charge where id = " + chargeId))
                .anyMatch(v -> v.startsWith("I-11h") && v.contains("origin " + origin.getId()));
        assertThat(afterInRollback(() -> invariants.lateFeesCoverEveryChargeableDayAt(today), "delete from " + corrections,
                "delete from late_fee_charge where id = " + chargeId,
                "update configuration_version set config_values = jsonb_set(config_values, '{lateFeeChargingEnabled}', 'false') where id = " + versionId)).isEmpty();
        assertThat(invariants.lateFeesRespectTheRedefinedI11()).isEmpty();
        invariants.assertAllBillingInvariantsHold();
    }
}
