package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.account.domain.CurrentAccountEntry;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentService;
import com.red_escuela.backend.enrollment.service.EnrollmentWithdrawal;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-378")
class LateFeePaymentDateAnnulmentIT extends AbstractLateFeeScenarioIT {

    @Autowired
    private CourseEnrollmentService courseEnrollmentService;

    @AfterEach
    void resetClock() {
        MutableClock.INSTANCE.reset();
    }

    private Long debitNoteIdOf(Invoice origin, int index) {
        return (Long) chargesOf(origin).get(index).get("debit_note_id");
    }

    private List<Map<String, Object>> creditNotesOf(Long invoiceId) {
        return jdbc.queryForList("select * from credit_note where invoice_id = ? order by id", invoiceId);
    }

    private List<Map<String, Object>> correctionsOf(Long invoiceId) {
        return jdbc.queryForList("select * from late_fee_charge_correction where debit_note_id = ? order by first_corrected_day", invoiceId);
    }

    private BigDecimal creditedTotalOf(Long invoiceId) {
        return jdbc.queryForObject("select coalesce(sum(total_amount), 0) from credit_note where invoice_id = ?", BigDecimal.class, invoiceId);
    }

    private String paymentStatusOf(Long invoiceId) {
        return jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoiceId);
    }

    @Test
    @Tag("D-202")
    @Tag("D-266")
    void paymentDatedBeforeTwoClosesAnnulsTheLaterDebitNoteInFull() {
        arrange(2401);
        publishDefaults(2401);
        Invoice origin = overdueInstallmentDueOn(d(2401, 3, 11));
        reconcile(origin, d(2401, 4, 1));
        reconcile(origin, d(2401, 5, 1));
        Long marchNote = debitNoteIdOf(origin, 0);
        Long aprilNote = debitNoteIdOf(origin, 1);
        MutableClock.INSTANCE.setDate(d(2401, 5, 2));

        payInFull(origin, d(2401, 3, 28), d(2401, 5, 2));
        awaitEventsDrained();

        assertThat(creditedTotalOf(marchNote)).isEqualByComparingTo("600.00");
        assertThat(paymentStatusOf(marchNote)).isEqualTo("PENDING");
        assertThat(creditedTotalOf(aprilNote)).isEqualByComparingTo("6000.00");
        assertThat(paymentStatusOf(aprilNote)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select count(charge_key_released_at)::text from invoice_line where invoice_id = ?", String.class, aprilNote)).isEqualTo("0");
        assertThat(correctionsOf(aprilNote)).hasSize(1);
        assertThat(correctionsOf(aprilNote).get(0)).containsEntry("corrected_days", 30);
        reconcile(origin, d(2401, 6, 1));
        assertThat(chargesOf(origin)).hasSize(2);
        assertScenarioInvariants(d(2401, 6, 1));
    }

    @Test
    @Tag("D-331")
    void paymentWithinGraceLoadedAfterACloseAnnulsEveryChargedDay() {
        arrange(2402);
        publish(10, "0.002", 10, true, true, d(2402, 1, 1));
        Invoice origin = overdueInstallmentDueOn(d(2402, 3, 11));
        reconcile(origin, d(2402, 4, 2));
        Long debitNoteId = debitNoteIdOf(origin, 0);
        MutableClock.INSTANCE.setDate(d(2402, 4, 2));

        payInFull(origin, d(2402, 3, 20), d(2402, 4, 2));
        awaitEventsDrained();

        assertThat(summaryOf(origin)).containsExactly("2402-03-12..2402-03-31:20:4000.00");
        assertThat(creditedTotalOf(debitNoteId)).isEqualByComparingTo("4000.00");
        assertThat(paymentStatusOf(debitNoteId)).isEqualTo("CANCELLED");
        assertThat(correctionsOf(debitNoteId)).hasSize(1);
        assertThat(correctionsOf(debitNoteId).get(0)).containsEntry("cause", "PAYMENT_WITHIN_GRACE").containsEntry("corrected_days", 20);
        assertScenarioInvariants(d(2402, 4, 2));
    }

    private Invoice installmentWithGraceClosedAndPaidOn(int year, LocalDate paymentDate) {
        arrange(year);
        publish(10, "0.002", 10, true, true, d(year, 1, 1));
        Invoice origin = overdueInstallmentDueOn(d(year, 3, 11));
        reconcile(origin, d(year, 4, 2));
        MutableClock.INSTANCE.setDate(d(year, 4, 2));
        payInFull(origin, paymentDate, d(year, 4, 2));
        awaitEventsDrained();
        return origin;
    }

    @Test
    @Tag("D-331")
    void graceBorderDecidesBetweenFullAndPartialAnnulment() {
        Invoice lastDayOfGrace = installmentWithGraceClosedAndPaidOn(2403, d(2403, 3, 21));
        Long fullNote = debitNoteIdOf(lastDayOfGrace, 0);
        assertThat(correctionsOf(fullNote)).hasSize(1);
        assertThat(correctionsOf(fullNote).get(0)).containsEntry("cause", "PAYMENT_WITHIN_GRACE").containsEntry("corrected_days", 20);
        assertThat(creditedTotalOf(fullNote)).isEqualByComparingTo("4000.00");
        assertScenarioInvariants(d(2403, 4, 2));

        Invoice firstDayAfterGrace = installmentWithGraceClosedAndPaidOn(2407, d(2407, 3, 22));
        Long partialNote = debitNoteIdOf(firstDayAfterGrace, 0);
        assertThat(correctionsOf(partialNote)).hasSize(1);
        assertThat(correctionsOf(partialNote).get(0)).containsEntry("cause", "PAYMENT_DATED_BEFORE_CHARGED_DAYS").containsEntry("corrected_days", 9);
        assertThat(correctionsOf(partialNote).get(0).get("first_corrected_day").toString()).isEqualTo("2407-03-23");
        assertThat(creditedTotalOf(partialNote)).isEqualByComparingTo("1800.00");
        assertScenarioInvariants(d(2407, 4, 2));
    }

    @Test
    @Tag("D-159")
    @Tag("D-144")
    void annulmentOfAnAlreadyPaidDebitNoteLeavesTheCreditInFavour() {
        arrange(2404);
        publishDefaults(2404);
        Invoice origin = overdueInstallmentDueOn(d(2404, 3, 11));
        reconcile(origin, d(2404, 4, 2));
        Long debitNoteId = debitNoteIdOf(origin, 0);
        MutableClock.INSTANCE.setDate(d(2404, 4, 2));

        receiveMoney(origin, "104000.00", d(2404, 3, 28));
        allocateOn(origin, d(2404, 4, 2));
        awaitEventsDrained();

        assertThat(paymentStatusOf(debitNoteId)).isEqualTo("PAID");
        List<Map<String, Object>> creditNotes = creditNotesOf(debitNoteId);
        assertThat(creditNotes).hasSize(1);
        assertThat((BigDecimal) creditNotes.get(0).get("free_credit_amount")).isEqualByComparingTo("600.00");
        assertThat((BigDecimal) creditNotes.get(0).get("offset_amount")).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = ?", BigDecimal.class, origin.getStudentCurrentAccountId()))
                .isEqualByComparingTo("600.00");
        assertScenarioInvariants(d(2404, 4, 2));
    }

    @Test
    @Tag("D-375")
    void paymentAnnulmentNeverOverlapsAWithdrawalCorrection() {
        arrange(2405);
        publishDefaults(2405);
        Invoice origin = overdueInstallmentDueOn(d(2405, 3, 11));
        reconcile(origin, d(2405, 4, 2));
        Long debitNoteId = debitNoteIdOf(origin, 0);
        MutableClock.INSTANCE.setDate(d(2405, 4, 3));
        courseEnrollmentService.withdraw(enrollment.getId(), EnrollmentWithdrawal.voluntaryRegisteredBy(d(2405, 3, 25), person.getId()));
        awaitEventsDrained();
        assertThat(correctionsOf(debitNoteId)).hasSize(1);
        assertThat(correctionsOf(debitNoteId).get(0)).containsEntry("cause", "BACKDATED_WITHDRAWAL").containsEntry("corrected_days", 6);
        MutableClock.INSTANCE.setDate(d(2405, 4, 4));

        payInFull(origin, d(2405, 3, 20), d(2405, 4, 4));
        awaitEventsDrained();

        List<Map<String, Object>> corrections = correctionsOf(debitNoteId);
        assertThat(corrections).hasSize(2);
        assertThat(corrections.get(0)).containsEntry("cause", "PAYMENT_DATED_BEFORE_CHARGED_DAYS").containsEntry("corrected_days", 5);
        assertThat(corrections.get(0).get("first_corrected_day").toString()).isEqualTo("2405-03-21");
        assertThat(corrections.get(0).get("last_corrected_day").toString()).isEqualTo("2405-03-25");
        assertThat(corrections.get(1).get("first_corrected_day").toString()).isEqualTo("2405-03-26");
        assertThat(creditedTotalOf(debitNoteId)).isEqualByComparingTo("2200.00");
        assertThat(creditedTotalOf(debitNoteId)).isLessThanOrEqualTo(new BigDecimal("4000.00"));
        assertScenarioInvariants(d(2405, 4, 4));
    }

    @Test
    @Tag("D-244")
    @Tag("D-290")
    @Tag("D-376")
    void annulmentCreditNoteIsAuditedWithThePaymentAndTheRecipient() {
        arrange(2406);
        publishDefaults(2406);
        Invoice origin = overdueInstallmentDueOn(d(2406, 3, 11));
        reconcile(origin, d(2406, 4, 2));
        Long debitNoteId = debitNoteIdOf(origin, 0);
        MutableClock.INSTANCE.setDate(d(2406, 4, 2));

        CurrentAccountEntry credit = payInFull(origin, d(2406, 3, 28), d(2406, 4, 2));
        awaitEventsDrained();

        Map<String, Object> creditNote = creditNotesOf(debitNoteId).get(0);
        assertThat(creditNote).containsEntry("reason", "LATE_FEE_ANNULMENT").containsEntry("issued_by_process", "LateFeePaymentDateCorrection");
        assertThat(creditNote.get("authorization_request_id")).isNull();
        assertThat(creditNote.get("issued_by_person_id")).isNull();
        assertThat(creditNote.get("fact_date").toString()).isEqualTo("2406-04-02");
        assertThat(creditNote.get("justification").toString()).contains("3 días posteriores al pago del 28/03/2406").contains(
                jdbc.queryForObject("select invoice_number from invoice where id = ?", String.class, debitNoteId));
        assertThat(jdbc.queryForObject("select recipient_snapshot = (select student_snapshot from invoice where id = ?) from credit_note where id = ?", Boolean.class, debitNoteId,
                creditNote.get("id"))).isTrue();
        Long settlementAllocationId = (Long) correctionsOf(debitNoteId).get(0).get("settlement_allocation_id");
        assertThat(jdbc.queryForList("select payment_id from payment_allocation_source where payment_allocation_id = ?", Long.class, settlementAllocationId))
                .containsExactly(credit.getPaymentId());
        assertThat(jdbc.queryForObject("select invoice_id from payment_allocation where id = ?", Long.class, settlementAllocationId)).isEqualTo(origin.getId());
        assertThat(jdbc.queryForObject("select count(*) from authorization_request_invoice where invoice_id = ?", Long.class, debitNoteId)).isZero();
        assertScenarioInvariants(d(2406, 4, 2));
    }
}
