package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.exception.IllegalInvoicePaymentStatusTransitionException;
import com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.billing.note.domain.CreditableLineFacts;
import com.red_escuela.backend.billing.note.domain.CreditedInvoiceFacts;
import com.red_escuela.backend.billing.note.domain.SystemCancellationOrigin;
import com.red_escuela.backend.billing.note.exception.CreditNoteDirectorApprovalRequiredException;
import com.red_escuela.backend.billing.note.exception.CreditNoteIssuingRejectedException;
import com.red_escuela.backend.billing.note.exception.CreditNoteJustificationRequiredException;
import com.red_escuela.backend.billing.note.exception.InvalidCreditNoteReasonException;
import com.red_escuela.backend.billing.note.exception.PendingBankTransferOnInvoiceException;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ars;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-127")
class InvoiceCancellationPoliciesTest {

    private static CreditedInvoiceFacts facts(InvoiceDocumentStatus document, InvoicePaymentStatus payment, String outstanding, String paid, String cancellable) {
        return new CreditedInvoiceFacts(5L, document, payment, ars(outstanding), ars(paid), "30111222",
                List.of(new CreditableLineFacts(1, ars(cancellable), ars("0.00"))));
    }

    private static CreditedInvoiceFacts open(String outstanding) {
        return facts(InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.PENDING, outstanding, "0.00", outstanding);
    }

    private static CancelInvoiceCommand command(CreditNoteReason reason, String justification) {
        return CancelInvoiceCommand.byPerson(5L, reason, justification, 9L, false);
    }

    private static InvoiceCancellationContext context(CancelInvoiceCommand command, CreditedInvoiceFacts invoice) {
        return new InvoiceCancellationContext(command, invoice);
    }

    private static PolicyVerdict evaluate(Policy<InvoiceCancellationContext> policy, CancelInvoiceCommand command, CreditedInvoiceFacts invoice) {
        return policy.evaluateRule(context(command, invoice));
    }

    @Test
    @Tag("B2-01")
    @Tag("D-091")
    void cancellationNeedsAnOpenIssuedInvoiceWithoutPaymentsCoveredByTheCreditNote() {
        Policy<InvoiceCancellationContext> policy = new CancellationByFullCreditNotePolicy();
        CancelInvoiceCommand command = command(CreditNoteReason.BILLING_ERROR, "x");

        assertThat(evaluate(policy, command, open("80000.00")).allowed()).isTrue();
        assertThat(evaluate(policy, command, facts(InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.OVERDUE, "80000.00", "0.00", "80000.00")).allowed()).isTrue();
        PolicyVerdict partiallyCovered = evaluate(policy, command, facts(InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.PENDING, "80000.00", "0.00", "79999.99"));
        assertThat(partiallyCovered.allowed()).isFalse();
        assertThat(partiallyCovered.code()).isEqualTo("ILLEGAL_INVOICE_PAYMENT_STATUS_TRANSITION");
        assertThat(evaluate(policy, command, facts(InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.PENDING, "80000.00", "0.01", "80000.00")).allowed()).isFalse();
        assertThat(evaluate(policy, command, facts(InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.PAID, "0.00", "80000.00", "0.00")).allowed()).isFalse();
        assertThat(evaluate(policy, command, facts(InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.CANCELLED, "0.00", "0.00", "0.00")).allowed()).isFalse();
        assertThat(evaluate(policy, command, facts(InvoiceDocumentStatus.DRAFT, InvoicePaymentStatus.PENDING, "80000.00", "0.00", "80000.00")).allowed()).isFalse();
    }

    @Test
    @Tag("B2-02")
    @Tag("D-238")
    void onlyCancellationBillingErrorAndLateFeeAnnulmentAreAcceptedAsReasons() {
        Policy<InvoiceCancellationContext> policy = new CancellationReasonPolicy();

        for (CreditNoteReason reason : List.of(CreditNoteReason.CANCELLATION, CreditNoteReason.BILLING_ERROR, CreditNoteReason.LATE_FEE_ANNULMENT)) {
            assertThat(evaluate(policy, command(reason, "x"), open("1.00")).allowed()).isTrue();
        }
        PolicyVerdict withdrawal = evaluate(policy, command(CreditNoteReason.WITHDRAWAL, "x"), open("1.00"));
        assertThat(withdrawal.allowed()).isFalse();
        assertThat(withdrawal.code()).isEqualTo("INVALID_CREDIT_NOTE_REASON");
        assertThat(evaluate(policy, command(null, "x"), open("1.00")).code()).isEqualTo("INVALID_CREDIT_NOTE_REASON");
    }

    @Test
    @Tag("B2-03")
    @Tag("D-165")
    void theJustificationIsRequiredAndLimitedToAThousandCharacters() {
        Policy<InvoiceCancellationContext> policy = new CancellationJustificationPolicy();

        assertThat(evaluate(policy, command(CreditNoteReason.CANCELLATION, ""), open("1.00")).code()).isEqualTo("CREDIT_NOTE_JUSTIFICATION_REQUIRED");
        assertThat(evaluate(policy, command(CreditNoteReason.CANCELLATION, "  "), open("1.00")).allowed()).isFalse();
        assertThat(evaluate(policy, command(CreditNoteReason.CANCELLATION, null), open("1.00")).allowed()).isFalse();
        assertThat(evaluate(policy, command(CreditNoteReason.CANCELLATION, "x".repeat(1000)), open("1.00")).allowed()).isTrue();
        assertThat(evaluate(policy, command(CreditNoteReason.CANCELLATION, "x".repeat(1001)), open("1.00")).allowed()).isFalse();
    }

    @Test
    @Tag("B2-04")
    @Tag("D-229")
    @Tag("D-300")
    @Tag("D-242")
    void onlyAProcessActorWithAnOriginCancelsWithoutTheDirector() {
        Policy<InvoiceCancellationContext> policy = new SystemInitiatedCancellationPolicy();

        for (SystemCancellationOrigin origin : SystemCancellationOrigin.values()) {
            assertThat(evaluate(policy, CancelInvoiceCommand.bySystem(5L, origin, "x"), open("1.00")).allowed()).isTrue();
        }
        assertThat(evaluate(policy, new CancelInvoiceCommand(5L, CreditNoteReason.CANCELLATION, "x", null, false, null, null), open("1.00")).code())
                .isEqualTo("CREDIT_NOTE_DIRECTOR_APPROVAL_REQUIRED");
        assertThat(evaluate(policy, new CancelInvoiceCommand(5L, CreditNoteReason.CANCELLATION, "x", 9L, false, SystemCancellationOrigin.ADMISSION_EXPIRED, null), open("1.00"))
                .code()).isEqualTo("CREDIT_NOTE_DIRECTOR_APPROVAL_REQUIRED");
        assertThat(evaluate(policy, command(CreditNoteReason.CANCELLATION, "x"), open("1.00")).allowed()).isTrue();
        assertThat(command(CreditNoteReason.CANCELLATION, "x").requestedByDirector()).isFalse();
        assertThat(CancelInvoiceCommand.bySystem(5L, SystemCancellationOrigin.ADMISSION_EXPIRED, "x").requestedByDirector()).isTrue();
    }

    @Test
    @Tag("D-153")
    void aPendingTransferBlocksTheCancellation() {
        Policy<InvoiceCancellationContext> policy = new CancellationInvoiceNotUnderReviewPolicy();

        assertThat(evaluate(policy, command(CreditNoteReason.CANCELLATION, "x"), open("1.00")).allowed()).isTrue();
        assertThat(evaluate(policy, command(CreditNoteReason.CANCELLATION, "x"),
                facts(InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.PAYMENT_UNDER_REVIEW, "1.00", "0.00", "1.00")).code()).isEqualTo("PENDING_BANK_TRANSFER_ON_INVOICE");
    }

    @Test
    @Tag("D-127")
    void everyPolicyNamesItselfAndEveryViolationMapsToItsException() {
        List<Policy<InvoiceCancellationContext>> policies = List.of(new CancellationByFullCreditNotePolicy(), new CancellationReasonPolicy(),
                new CancellationJustificationPolicy(), new SystemInitiatedCancellationPolicy(), new CancellationInvoiceNotUnderReviewPolicy());
        policies.forEach(policy -> assertThat(policy.ruleName()).isEqualTo(policy.getClass().getSimpleName()));

        assertThat(InvoiceCancellationRejections.rejectionOf(violation("CREDIT_NOTE_JUSTIFICATION_REQUIRED"), InvoicePaymentStatus.PENDING)).isInstanceOf(CreditNoteJustificationRequiredException.class);
        assertThat(InvoiceCancellationRejections.rejectionOf(violation("INVALID_CREDIT_NOTE_REASON"), InvoicePaymentStatus.PENDING)).isInstanceOf(InvalidCreditNoteReasonException.class);
        assertThat(InvoiceCancellationRejections.rejectionOf(violation("PENDING_BANK_TRANSFER_ON_INVOICE"), InvoicePaymentStatus.PENDING)).isInstanceOf(PendingBankTransferOnInvoiceException.class);
        assertThat(InvoiceCancellationRejections.rejectionOf(violation("CREDIT_NOTE_DIRECTOR_APPROVAL_REQUIRED"), InvoicePaymentStatus.PENDING))
                .isInstanceOf(CreditNoteDirectorApprovalRequiredException.class);
        assertThat(InvoiceCancellationRejections.rejectionOf(violation("ILLEGAL_INVOICE_PAYMENT_STATUS_TRANSITION"), InvoicePaymentStatus.PAID))
                .isInstanceOf(IllegalInvoicePaymentStatusTransitionException.class);
        assertThat(InvoiceCancellationRejections.rejectionOf(violation("OTHER"), InvoicePaymentStatus.PAID)).isInstanceOf(CreditNoteIssuingRejectedException.class);
    }

    private static List<PolicyViolation> violation(String code) {
        return List.of(new PolicyViolation(code, "reason"));
    }
}
