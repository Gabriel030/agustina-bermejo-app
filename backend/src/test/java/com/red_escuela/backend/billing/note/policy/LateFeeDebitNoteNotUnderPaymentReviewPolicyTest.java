package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-153")
class LateFeeDebitNoteNotUnderPaymentReviewPolicyTest {
    private final LateFeeDebitNoteNotUnderPaymentReviewPolicy policy = new LateFeeDebitNoteNotUnderPaymentReviewPolicy();

    @Test
    void aLateFeeUnderPaymentReviewIsDeniedWithItsOwnCodeAndItsId() {
        PolicyVerdict verdict = policy.evaluateRule(new LateFeeDebitNoteAnnulmentContext(77L, "ND-00000077", InvoicePaymentStatus.PAYMENT_UNDER_REVIEW));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("LATE_FEE_UNDER_PAYMENT_REVIEW");
        assertThat(verdict.reason()).contains("ND-00000077").contains("id 77");
    }

    @Test
    void aLateFeeInAnyOtherStatusIsAllowed() {
        for (InvoicePaymentStatus status : InvoicePaymentStatus.values()) {
            if (status != InvoicePaymentStatus.PAYMENT_UNDER_REVIEW) {
                assertThat(policy.evaluateRule(new LateFeeDebitNoteAnnulmentContext(77L, "ND-00000077", status)).allowed()).as(status.name()).isTrue();
            }
        }
    }
}
