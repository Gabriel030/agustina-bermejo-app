package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentService;
import com.red_escuela.backend.enrollment.service.EnrollmentWithdrawal;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-148")
@Tag("D-293")
@Tag("D-249")
@SlowTest(reason = "Shares the heavy Spring context of the late fee scenario hierarchy: when it is the first class of its shard it absorbs the whole context start-up")
class EnrollmentWithdrawalLateFeeFreezeIT extends AbstractLateFeeScenarioIT {

    @Autowired
    private CourseEnrollmentService courseEnrollmentService;

    @Test
    @Tag("D-375")
    void aWithdrawalInformedForAprilTwentyEighthCreditsTheLateFeeOfTheTwentyNinthAndThirtieth() {
        arrange(2107);
        publishDefaults(2107);
        Invoice origin = overdueInstallmentDueOn(d(2107, 4, 11));
        reconcile(origin, d(2107, 5, 1));
        assertThat(summaryOf(origin)).containsExactly("2107-04-12..2107-04-30:19:3800.00");
        long issuedDebitNotes = jdbc.queryForObject("select count(*) from invoice where origin_invoice_id = ?", Long.class, origin.getId());
        Long debitNoteId = (Long) chargesOf(origin).get(0).get("debit_note_id");
        MutableClock.INSTANCE.setDate(d(2107, 5, 3));

        courseEnrollmentService.withdraw(enrollment.getId(), EnrollmentWithdrawal.voluntaryRegisteredBy(d(2107, 4, 28), person.getId()));
        awaitEventsDrained();
        reconcile(origin, d(2107, 5, 4));
        reconcile(origin, d(2107, 6, 1));

        assertThat(jdbc.queryForObject("select completion_date::date::text from course_enrollment where id = ?", String.class, enrollment.getId())).isEqualTo("2107-04-28");
        assertThat(jdbc.queryForObject("select withdrawal_registered_on::text from course_enrollment where id = ?", String.class, enrollment.getId())).isEqualTo("2107-05-03");
        assertThat(summaryOf(origin)).containsExactly("2107-04-12..2107-04-30:19:3800.00");
        assertThat(jdbc.queryForObject("select count(*) from invoice where origin_invoice_id = ?", Long.class, origin.getId())).isEqualTo(issuedDebitNotes);
        List<Map<String, Object>> creditNotes = jdbc.queryForList("select * from credit_note where invoice_id = ?", debitNoteId);
        assertThat(creditNotes).hasSize(1);
        assertThat(creditNotes.get(0)).containsEntry("reason", "LATE_FEE_CORRECTION").containsEntry("issued_by_process", "LateFeeWithdrawalCorrection")
                .containsEntry("fact_date", java.sql.Date.valueOf(d(2107, 5, 3)));
        assertThat((BigDecimal) creditNotes.get(0).get("total_amount")).isEqualByComparingTo("400.00");
        assertThat((BigDecimal) creditNotes.get(0).get("offset_amount")).isEqualByComparingTo("400.00");
        assertThat(creditNotes.get(0).get("authorization_request_id")).isNull();
        assertThat(jdbc.queryForObject("select total from invoice where id = ?", BigDecimal.class, debitNoteId)).isEqualByComparingTo("3800.00");
        assertThat(jdbc.queryForObject("select outstanding_amount from invoice where id = ?", BigDecimal.class, debitNoteId)).isEqualByComparingTo("3400.00");
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, debitNoteId)).isNotEqualTo("CANCELLED");
        assertThat(jdbc.queryForList("select first_corrected_day::text f, last_corrected_day::text l, corrected_days d, cause c from late_fee_charge_correction where debit_note_id = ?", debitNoteId))
                .containsExactly(Map.of("f", "2107-04-29", "l", "2107-04-30", "d", 2, "c", "BACKDATED_WITHDRAWAL"));
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void aWithdrawalRegisteredWithoutInformedDateFreezesTheLateFeeFromTheRegistrationDay() {
        arrange(2108);
        publishDefaults(2108);
        Invoice origin = overdueInstallmentDueOn(d(2108, 4, 11));
        reconcile(origin, d(2108, 5, 1));
        MutableClock.INSTANCE.setDate(d(2108, 5, 3));

        courseEnrollmentService.withdraw(enrollment.getId(), EnrollmentWithdrawal.voluntaryRegisteredBy(null, person.getId()));
        reconcile(origin, d(2108, 6, 1));

        assertThat(jdbc.queryForObject("select completion_date::date::text from course_enrollment where id = ?", String.class, enrollment.getId())).isEqualTo("2108-05-03");
        assertThat(summaryOf(origin)).containsExactly("2108-04-12..2108-04-30:19:3800.00", "2108-05-01..2108-05-03:3:600.00");
        invariants.assertAllBillingInvariantsHold();
    }
}
