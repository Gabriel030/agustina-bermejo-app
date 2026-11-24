package com.red_escuela.backend.graduation.debtexception.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DebtExceptionAuditDetailTest {

    @ParameterizedTest
    @ValueSource(strings = {"documentId=12", "code=EXC-7", "engineVersion=v1.2", "templateVersion=3", "documentId=1;code=A_b-c.d"})
    void validPairsAreAccepted(String detail) {
        assertThat(DebtExceptionAuditDetail.requireAllowed(detail)).isEqualTo(detail);
    }

    @Test
    void nullAndBlankAreAcceptedAsNoDetail() {
        assertThat(DebtExceptionAuditDetail.requireAllowed(null)).isNull();
        assertThat(DebtExceptionAuditDetail.requireAllowed("")).isNull();
        assertThat(DebtExceptionAuditDetail.requireAllowed("   ")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"code=con espacio", "code=linea\nnueva", "code=A;", ";code=A", "code=A=B", "code=A;code=B",
            "reason=algo", "documentId", "=1", "documentId=1;;code=A", "code="})
    void invalidDetailsAreRejected(String detail) {
        assertThatThrownBy(() -> DebtExceptionAuditDetail.requireAllowed(detail)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void valueOfSixtyFiveCharactersIsRejectedAndSixtyFourIsAccepted() {
        assertThat(DebtExceptionAuditDetail.requireAllowed("code=" + "a".repeat(64))).isNotNull();
        assertThatThrownBy(() -> DebtExceptionAuditDetail.requireAllowed("code=" + "a".repeat(65)))
                .isInstanceOf(IllegalStateException.class);
    }
}
