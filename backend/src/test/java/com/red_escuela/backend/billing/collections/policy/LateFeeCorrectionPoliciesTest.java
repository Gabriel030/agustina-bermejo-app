package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-375")
class LateFeeCorrectionPoliciesTest {
    private final CorrectionTargetsLiveLateFeeDebitNotePolicy target = new CorrectionTargetsLiveLateFeeDebitNotePolicy();
    private final CorrectionDebitNoteNotUnderPaymentReviewPolicy review = new CorrectionDebitNoteNotUnderPaymentReviewPolicy();

    private static LateFeeCorrectionContext context(InvoiceDocumentType documentType, InvoiceType invoiceType, InvoiceDocumentStatus documentStatus, InvoicePaymentStatus paymentStatus) {
        return new LateFeeCorrectionContext(1L, documentType, invoiceType, documentStatus, paymentStatus);
    }

    @Test
    @Tag("D-244")
    void anIssuedLateFeeDebitNoteInAnyLiveStatusIsATarget() {
        for (InvoicePaymentStatus status : new InvoicePaymentStatus[]{InvoicePaymentStatus.PENDING, InvoicePaymentStatus.OVERDUE, InvoicePaymentStatus.PAID,
                InvoicePaymentStatus.PAYMENT_UNDER_REVIEW}) {
            assertThat(target.evaluateRule(context(InvoiceDocumentType.DEBIT_NOTE, InvoiceType.LATE_FEE, InvoiceDocumentStatus.ISSUED, status)).allowed()).isTrue();
        }
    }

    @Test
    @Tag("D-244")
    void anInvoiceACancelledDebitNoteOrADraftIsNotATarget() {
        assertThat(target.evaluateRule(context(InvoiceDocumentType.INVOICE, InvoiceType.MONTHLY_INSTALLMENT, InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.PENDING)).code())
                .isEqualTo("LATE_FEE_CORRECTION_TARGET_INVALID");
        assertThat(target.evaluateRule(context(InvoiceDocumentType.DEBIT_NOTE, InvoiceType.LATE_FEE, InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.CANCELLED)).allowed()).isFalse();
        assertThat(target.evaluateRule(context(InvoiceDocumentType.DEBIT_NOTE, InvoiceType.LATE_FEE, InvoiceDocumentStatus.DRAFT, InvoicePaymentStatus.PENDING)).allowed()).isFalse();
    }

    @Test
    @Tag("D-153")
    void onlyTheStatusUnderPaymentReviewDefersTheCorrection() {
        assertThat(review.evaluateRule(context(InvoiceDocumentType.DEBIT_NOTE, InvoiceType.LATE_FEE, InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.PAYMENT_UNDER_REVIEW)).code())
                .isEqualTo("PENDING_BANK_TRANSFER_ON_INVOICE");
        assertThat(review.evaluateRule(context(InvoiceDocumentType.DEBIT_NOTE, InvoiceType.LATE_FEE, InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.OVERDUE)).allowed()).isTrue();
    }
}
