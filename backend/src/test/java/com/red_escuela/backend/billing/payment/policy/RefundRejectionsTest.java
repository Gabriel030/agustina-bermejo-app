package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-159")
@Tag("D-264")
@Tag("D-267")
class RefundRejectionsTest {

    @ParameterizedTest
    @CsvSource({
            "REFUND_AMOUNT_INVALID,400",
            "REFUND_DESTINATION_INVALID,400",
            "REFUND_DESTINATION_REQUIRED,400",
            "REFUND_COMPLETION_INVALID,400",
            "REFUND_FAILURE_REASON_INVALID,400",
            "REFUND_BLOCKED_BY_OVERDUE_DEBT,409",
            "INSUFFICIENT_ACCOUNT_CREDIT,409",
            "REFUND_EXCEEDS_AVAILABLE_AMOUNT,409",
            "REFUND_METHOD_MISMATCH,409"})
    void everyViolationCodeMapsToAnExceptionWithThatSameCodeAndItsHttpFamily(String code, int status) {
        RuntimeException rejection = RefundRejections.rejectionOf(List.of(new PolicyViolation(code, "motivo")));

        assertThat(ErrorCodeResponse.of(rejection).code()).isEqualTo(code);
        assertThat(status == 400 ? rejection instanceof BadRequestException : rejection instanceof ConflictException).isTrue();
    }

    @Test
    void anUnknownCodeIsAProgrammingError() {
        assertThatThrownBy(() -> RefundRejections.rejectionOf(List.of(new PolicyViolation("OTHER", "x")))).isInstanceOf(IllegalStateException.class);
    }
}
