package com.red_escuela.backend.shared.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditValueMaskerTest {

    @Test
    void sensitiveFieldsAreMaskedInJson() {
        String masked = AuditValueMasker.mask(
                "{\"passwordHash\":\"$2a$10$Xk3Qa9\",\"token\":\"abc123\",\"refreshToken\":\"r-1\","
                        + "\"code\":\"481516\",\"secret\":\"s\",\"apiKey\":\"k\",\"cbu\":\"0170099220000067797370\","
                        + "\"password\":\"hunter2\",\"status\":\"ACTIVE\"}");

        assertThat(masked).doesNotContain("$2a$10$Xk3Qa9", "abc123", "r-1", "481516", "hunter2", "0170099220000067797370")
                .contains("\"status\":\"ACTIVE\"")
                .contains("\"passwordHash\":\"***\"")
                .contains("\"token\":\"***\"")
                .contains("\"cbu\":\"***\"");
    }

    @Test
    void sensitiveFieldsAreMaskedInPlainText() {
        String masked = AuditValueMasker.mask("password=hunter2 token: abc123, estado=ACTIVO");

        assertThat(masked).doesNotContain("hunter2", "abc123").contains("estado=ACTIVO");
    }

    @Test
    void sensitiveFieldsAreMaskedWhenTheValueWasTruncated() {
        String masked = AuditValueMasker.mask("{\"email\":\"a@b.com\",\"resetToken\":\"9f8a1c77e0b2");

        assertThat(masked).doesNotContain("9f8a1c77e0b2").contains("a@b.com");
    }

    @Test
    void numericAndCompositeKeyNamesAreMasked() {
        String masked = AuditValueMasker.mask("{\"verification_code\":481516,\"newPassword\":\"x y, z\",\"name\":\"Ana\"}");

        assertThat(masked).doesNotContain("481516", "x y, z").contains("\"name\":\"Ana\"");
    }

    @Test
    void nullValuesAndNonSensitiveTextAreLeftUntouched() {
        assertThat(AuditValueMasker.mask(null)).isNull();
        assertThat(AuditValueMasker.mask("")).isEmpty();
        assertThat(AuditValueMasker.mask("{\"token\":null,\"status\":\"WITHDRAWN\"}"))
                .isEqualTo("{\"token\":null,\"status\":\"WITHDRAWN\"}");
        assertThat(AuditValueMasker.mask("ACTIVE")).isEqualTo("ACTIVE");
    }
}
