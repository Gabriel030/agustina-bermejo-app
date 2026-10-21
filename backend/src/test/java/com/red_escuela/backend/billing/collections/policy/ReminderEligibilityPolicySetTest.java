package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.billing.collections.CollectionsPolicySetsConfiguration;
import com.red_escuela.backend.billing.collections.domain.ReminderInvoiceFact;
import com.red_escuela.backend.billing.collections.domain.ReminderInvoiceFacts;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.policy.PolicyEvaluationResult;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderEligibilityPolicySetTest {
    private final PolicySet<ReminderInvoiceFact> policySet = new CollectionsPolicySetsConfiguration().collectionReminderEligibilityPolicySet();

    private PolicyEvaluationResult evaluate(InvoiceDocumentStatus documentStatus, InvoicePaymentStatus paymentStatus, boolean receiptUnderReview, boolean openCancellationRequest) {
        return policySet.evaluateRulesUntilFirstViolation(ReminderInvoiceFacts.fact(1L, null, InvoiceDocumentType.INVOICE, InvoiceType.MONTHLY_INSTALLMENT, documentStatus,
                paymentStatus, "2027-03-01", "2027-03-11", null, receiptUnderReview, openCancellationRequest));
    }

    private String firstViolationCode(PolicyEvaluationResult result) {
        return result.verdicts().stream().filter(verdict -> !verdict.allowed()).findFirst().orElseThrow().code();
    }

    @Test
    @Tag("D-354")
    void thePolicySetKeepsTheSpecOrderEligibleThenReceiptThenCancellation() {
        assertThat(policySet.policies()).extracting(policy -> policy.ruleName()).containsExactly(
                "ReminderEligibleInvoicePolicy", "ReceiptUnderReviewSuppressesReminderPolicy", "PendingCancellationSuppressesReminderPolicy");
    }

    @Test
    @Tag("D-354")
    void aCancelledInvoiceWithAReceiptFlagIsNotRemindable() {
        assertThat(firstViolationCode(evaluate(InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.CANCELLED, true, false))).isEqualTo("INVOICE_NOT_REMINDABLE");
    }

    @Test
    @Tag("D-346")
    void anIssuedInvoiceWithAReceiptUnderReviewIsSuppressedByTheReceipt() {
        assertThat(firstViolationCode(evaluate(InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.PENDING, true, false))).isEqualTo("RECEIPT_UNDER_REVIEW");
    }

    @Test
    @Tag("D-247")
    void anIssuedInvoiceWithAPendingCancellationIsSuppressedByTheRequest() {
        assertThat(firstViolationCode(evaluate(InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.OVERDUE, false, true))).isEqualTo("PENDING_CANCELLATION_REQUEST");
    }

    @Test
    @Tag("D-346")
    void anInvoiceUnderPaymentReviewIsReportedAsNotRemindableBecauseEligibilityRunsFirst() {
        assertThat(firstViolationCode(evaluate(InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.PAYMENT_UNDER_REVIEW, false, false))).isEqualTo("INVOICE_NOT_REMINDABLE");
    }

    @Test
    @Tag("D-354")
    void aPendingInvoiceWithoutBlockersPassesTheWholeSet() {
        assertThat(evaluate(InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.PENDING, false, false).verdicts()).hasSize(3).allMatch(verdict -> verdict.allowed());
    }
}
