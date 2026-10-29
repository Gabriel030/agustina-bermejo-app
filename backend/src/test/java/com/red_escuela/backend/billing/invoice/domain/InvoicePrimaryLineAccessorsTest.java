package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.billing.exception.InvoiceNotPayableException;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoicePrimaryLineAccessorsTest {

    @Test
    void primaryLineAccessorsExposeTheFirstLineValues() {
        Invoice invoice = InvoiceFactory.createMonthlyInstallmentInvoiceDraft(header(InvoiceType.MONTHLY_INSTALLMENT), draftOf(null, null, "K1"), Optional.empty(), ACTOR);

        assertThat(invoice.getFeeType()).isEqualTo(FeeType.MONTHLY_INSTALLMENT);
        assertThat(invoice.getPeriod()).isEqualTo(YearMonth.of(2027, 3));
        assertThat(invoice.getTemplateId()).isEqualTo(77L);
        assertThat(invoice.getEnrollmentFeeSlot()).isEqualTo(2);
        assertThat(invoice.currency()).isEqualTo(Currency.ARS);
    }

    static InvoiceLineDraft draftOf(Long programEnrollmentId, Long courseEnrollmentId, String chargeKey) {
        return new InvoiceLineDraft(FeeType.MONTHLY_INSTALLMENT, "Concepto", YearMonth.of(2027, 3), programEnrollmentId, courseEnrollmentId, null, null, 5L, chargeKey,
                ars("1000.00"), 77L, 2, null);
    }

    @Test
    void aDraftInvoiceIsNotPayableAndRequirePayableRejectsIt() {
        Invoice invoice = monthlyDraft("80000.00");

        assertThat(invoice.isPayable()).isFalse();
        assertThatThrownBy(invoice::requirePayable).isInstanceOf(InvoiceNotPayableException.class);
    }

    @Test
    void anIssuedPendingInvoiceIsPayable() {
        Invoice invoice = issuedMonthly("80000.00");

        assertThat(invoice.isPayable()).isTrue();
        invoice.requirePayable();
    }

    @Test
    void anOverdueInvoiceIsStillPayable() {
        Invoice invoice = issuedMonthly("80000.00");
        invoice.markInvoiceAsOverdue(LocalDate.of(2027, 4, 30), ACTOR);

        assertThat(invoice.isPayable()).isTrue();
        invoice.requirePayable();
    }

    @Test
    void anInvoiceUnderPaymentReviewIsNotPayable() {
        Invoice invoice = issuedMonthly("80000.00");
        invoice.markPaymentUnderReview(DUE_DATE, ACTOR, "comprobante");

        assertThat(invoice.isPayable()).isFalse();
        assertThatThrownBy(invoice::requirePayable).isInstanceOf(InvoiceNotPayableException.class);
    }

    @Test
    void findingEnrollmentIdsReturnsTheOnesOnTheLines() {
        Invoice program = InvoiceFactory.createMonthlyInstallmentInvoiceDraft(header(InvoiceType.MONTHLY_INSTALLMENT), draftOf(123L, null, "K2"), Optional.empty(), ACTOR);
        Invoice course = InvoiceFactory.createMonthlyInstallmentInvoiceDraft(header(InvoiceType.MONTHLY_INSTALLMENT), draftOf(null, 456L, "K3"), Optional.empty(), ACTOR);

        assertThat(program.findProgramEnrollmentId()).contains(123L);
        assertThat(program.findCourseEnrollmentId()).isEmpty();
        assertThat(course.findCourseEnrollmentId()).contains(456L);
        assertThat(course.findProgramEnrollmentId()).isEmpty();
    }

    @Test
    void assigningAnEnrollmentSetsItOnEveryLine() {
        List<InvoiceLineDraft> drafts = List.of(draftOf(null, null, "K4"), new InvoiceLineDraft(FeeType.MONTHLY_INSTALLMENT, "Otro", YearMonth.of(2027, 4), null, null, null, null, 5L, "K5",
                ars("1000.00"), 77L, 2, null));
        Invoice forProgram = InvoiceFactory.createAnnualUpfrontInvoiceDraft(header(InvoiceType.ANNUAL_UPFRONT), drafts, Map.of(), ACTOR);
        Invoice forCourse = InvoiceFactory.createAnnualUpfrontInvoiceDraft(header(InvoiceType.ANNUAL_UPFRONT), drafts, Map.of(), ACTOR);

        forProgram.assignInvoiceToProgramEnrollment(900L);
        forCourse.assignInvoiceToCourseEnrollment(901L);

        assertThat(forProgram.getLines()).hasSize(2).allSatisfy(line -> assertThat(line.getProgramEnrollmentId()).isEqualTo(900L));
        assertThat(forCourse.getLines()).hasSize(2).allSatisfy(line -> assertThat(line.getCourseEnrollmentId()).isEqualTo(901L));
    }

    @Test
    void everyStateChangingOperationRequiresBusinessDateAndActor() {
        assertRequiresContext((invoice, date) -> invoice.markInvoiceAsOverdue(date, ACTOR), (invoice, actor) -> invoice.markInvoiceAsOverdue(DUE_DATE, actor));
        assertRequiresContext((invoice, date) -> invoice.releasePaymentReview(date, 5, ACTOR, "r"), (invoice, actor) -> invoice.releasePaymentReview(DUE_DATE, 5, actor, "r"));
        assertRequiresContext((invoice, date) -> invoice.releasePaymentReviewWithoutLateTerms(date, ACTOR, "r"),
                (invoice, actor) -> invoice.releasePaymentReviewWithoutLateTerms(DUE_DATE, actor, "r"));
        assertRequiresContext((invoice, date) -> invoice.reversePaymentAllocation(date, 5, ACTOR, "r"), (invoice, actor) -> invoice.reversePaymentAllocation(DUE_DATE, 5, actor, "r"));
        assertRequiresContext((invoice, date) -> invoice.allocateFullInvoicePayment(ars("80000.00"), date, ACTOR),
                (invoice, actor) -> invoice.allocateFullInvoicePayment(ars("80000.00"), DUE_DATE, actor));
        InvoiceCreditNoteApplication application = creditNote(CreditNoteApplicationKind.CANCELLATION, "80000.00", "0.00");
        assertRequiresContext((invoice, date) -> invoice.applyCreditNote(application, date, ACTOR), (invoice, actor) -> invoice.applyCreditNote(application, DUE_DATE, actor));
    }

    private void assertRequiresContext(BiConsumer<Invoice, LocalDate> withDate, BiConsumer<Invoice, StatusChangeActor> withActor) {
        assertThatThrownBy(() -> withDate.accept(issuedMonthly("80000.00"), null)).isInstanceOf(NullPointerException.class).hasMessage("businessDate");
        assertThatThrownBy(() -> withActor.accept(issuedMonthly("80000.00"), null)).isInstanceOf(NullPointerException.class).hasMessage("actor");
    }

    @Test
    void releasingAReviewWithoutLateTermsReturnsThePendingInvoiceToPending() {
        Invoice invoice = issuedMonthly("80000.00");
        invoice.markPaymentUnderReview(DUE_DATE, ACTOR, "comprobante");

        invoice.releasePaymentReviewWithoutLateTerms(DUE_DATE.plusDays(30), ACTOR, "rechazado");

        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PENDING);
        assertThat(invoice.getStatusChanges().get(invoice.getStatusChanges().size() - 1).getReason()).isEqualTo("rechazado");
    }
}
