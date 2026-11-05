package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-262")
@Tag("D-265")
class PaymentWithoutPendingRefundPolicyTest {

    private static Money ars(String amount) {
        return Money.of(new BigDecimal(amount), Currency.ARS);
    }

    private static PaymentReversalCheck checkWithPending(String pending) {
        return PaymentReversalCheck.ofRevocation(ars("100000.00"), ars("0.00"), ars(pending));
    }

    @Test
    void aPaymentWithoutRequestedNorApprovedRefundPartsCanBeReversed() {
        PolicyVerdict verdict = new PaymentWithoutPendingRefundPolicy().evaluateRule(checkWithPending("0.00"));

        assertThat(verdict.allowed()).isTrue();
    }

    @Test
    void theSmallestPendingAmountBlocksTheReversalWithItsOwnCode() {
        PolicyVerdict verdict = new PaymentWithoutPendingRefundPolicy().evaluateRule(checkWithPending("0.01"));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("PAYMENT_HAS_PENDING_REFUND");
        assertThat(verdict.policy()).isEqualTo("PaymentWithoutPendingRefundPolicy");
    }

    @Test
    void theViolationCodeMapsToAConflictWithTheSameCode() {
        RuntimeException rejection = PaymentReversalRejections.rejectionOf(List.of(new PolicyViolation("PAYMENT_HAS_PENDING_REFUND", "motivo")));

        assertThat(rejection).isInstanceOf(ConflictException.class);
        assertThat(ErrorCodeResponse.of(rejection).code()).isEqualTo("PAYMENT_HAS_PENDING_REFUND");
    }
}
