package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.exception.CashPaymentAmountInvalidException;
import com.red_escuela.backend.billing.payment.exception.CashPaymentBackdateReasonRequiredException;
import com.red_escuela.backend.billing.payment.exception.CashPaymentDateTooOldException;
import com.red_escuela.backend.billing.payment.exception.CashPaymentPreviewOutdatedException;
import com.red_escuela.backend.billing.payment.exception.CashPaymentReceiptNumberInvalidException;
import com.red_escuela.backend.billing.payment.exception.IdempotencyKeyInvalidException;
import com.red_escuela.backend.billing.payment.exception.PaymentDateInFutureException;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-266")
@Tag("D-319")
class CashPaymentRejectionsTest {

    @ParameterizedTest
    @CsvSource({
            "IDEMPOTENCY_KEY_INVALID,IdempotencyKeyInvalidException",
            "CASH_PAYMENT_AMOUNT_INVALID,CashPaymentAmountInvalidException",
            "CASH_PAYMENT_RECEIPT_NUMBER_INVALID,CashPaymentReceiptNumberInvalidException",
            "PAYMENT_DATE_IN_FUTURE,PaymentDateInFutureException",
            "CASH_PAYMENT_DATE_TOO_OLD,CashPaymentDateTooOldException",
            "CASH_PAYMENT_BACKDATE_REASON_REQUIRED,CashPaymentBackdateReasonRequiredException",
            "CASH_PAYMENT_PREVIEW_OUTDATED,CashPaymentPreviewOutdatedException"})
    void everyViolationCodeMapsToItsOwnException(String code, String exceptionName) {
        RuntimeException rejection = CashPaymentRejections.rejectionOf(List.of(new PolicyViolation(code, "motivo")));

        assertThat(rejection.getClass().getSimpleName()).isEqualTo(exceptionName);
        assertThat(ErrorCodeResponse.of(rejection).code()).isEqualTo(code);
    }

    @Test
    void theCodesTheClientReadsMatchTheApprovedList() {
        assertThat(CashPaymentRejections.rejectionOf(List.of(new PolicyViolation("CASH_PAYMENT_PREVIEW_OUTDATED", "x"))))
                .isInstanceOf(CashPaymentPreviewOutdatedException.class);
        assertThat(CashPaymentRejections.rejectionOf(List.of(new PolicyViolation("CASH_PAYMENT_DATE_TOO_OLD", "x")))).isInstanceOf(CashPaymentDateTooOldException.class);
        assertThat(CashPaymentRejections.rejectionOf(List.of(new PolicyViolation("CASH_PAYMENT_BACKDATE_REASON_REQUIRED", "x"))))
                .isInstanceOf(CashPaymentBackdateReasonRequiredException.class);
        assertThat(CashPaymentRejections.rejectionOf(List.of(new PolicyViolation("PAYMENT_DATE_IN_FUTURE", "x")))).isInstanceOf(PaymentDateInFutureException.class);
        assertThat(CashPaymentRejections.rejectionOf(List.of(new PolicyViolation("IDEMPOTENCY_KEY_INVALID", "x")))).isInstanceOf(IdempotencyKeyInvalidException.class);
        assertThat(CashPaymentRejections.rejectionOf(List.of(new PolicyViolation("CASH_PAYMENT_AMOUNT_INVALID", "x")))).isInstanceOf(CashPaymentAmountInvalidException.class);
        assertThat(CashPaymentRejections.rejectionOf(List.of(new PolicyViolation("CASH_PAYMENT_RECEIPT_NUMBER_INVALID", "x"))))
                .isInstanceOf(CashPaymentReceiptNumberInvalidException.class);
    }

    @Test
    void anUnmappedViolationFailsLoudlyInsteadOfBecomingAGenericRejection() {
        assertThatThrownBy(() -> CashPaymentRejections.rejectionOf(List.of(new PolicyViolation("SOMETHING_ELSE", "x"))))
                .isInstanceOf(IllegalStateException.class);
    }
}
