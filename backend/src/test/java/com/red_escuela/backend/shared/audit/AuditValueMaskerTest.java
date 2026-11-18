package com.red_escuela.backend.shared.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditValueMaskerTest {

    @Test
    void sensitiveFieldsAreMaskedInFlatJson() {
        String masked = AuditValueMasker.mask(
                "{\"passwordHash\":\"$2a$10$Xk3Qa9\",\"token\":\"abc123\",\"refreshToken\":\"r-1\","
                        + "\"code\":\"481516\",\"secret\":\"s\",\"apiKey\":\"k\",\"cbu\":\"0170099220000067797370\","
                        + "\"password\":\"hunter2\",\"status\":\"ACTIVE\"}");

        assertThat(masked).doesNotContain("$2a$10$Xk3Qa9", "abc123", "r-1", "481516", "hunter2", "0170099220000067797370")
                .contains("\"status\":\"ACTIVE\"")
                .contains("\"passwordHash\":\"***\"")
                .contains("\"cbu\":\"***\"");
    }

    @Test
    void arrayValueAfterSensitiveKeyIsFullyMasked() {
        String masked = AuditValueMasker.mask("{\"password\":[\"a1\",\"b2\"],\"status\":\"ACTIVE\"}");

        assertThat(masked).doesNotContain("a1", "b2").isEqualTo("{\"password\":\"***\",\"status\":\"ACTIVE\"}");
    }

    @Test
    void objectValueAfterSensitiveKeyIsFullyMasked() {
        String masked = AuditValueMasker.mask("{\"password\":{\"x\":\"leak1\"}}");

        assertThat(masked).doesNotContain("leak1").isEqualTo("{\"password\":\"***\"}");
    }

    @Test
    void nestedObjectsAndArraysOfObjectsAreMaskedAtAnyDepth() {
        String masked = AuditValueMasker.mask(
                "{\"user\":{\"profile\":{\"newPassword\":\"deep1\"}},\"items\":[{\"token\":\"deep2\",\"id\":7},[{\"secret\":\"deep3\"}]]}");

        assertThat(masked).doesNotContain("deep1", "deep2", "deep3").contains("\"id\":7");
    }

    @Test
    void jsonEmbeddedInAStringIsParsedAndMasked() {
        String masked = AuditValueMasker.mask("{\"v\":\"{\\\"password\\\":\\\"leak2\\\",\\\"ok\\\":1}\"}");

        assertThat(masked).doesNotContain("leak2").contains("ok");
    }

    @Test
    void doubleEscapedJsonThatCannotBeParsedFailsClosed() {
        String masked = AuditValueMasker.mask("{\"v\":\"{\\\"password\\\":\\\"leak3\\\"\"}");

        assertThat(masked).doesNotContain("leak3");
    }

    @Test
    void malformedOrTruncatedJsonFailsClosed() {
        assertThat(AuditValueMasker.mask("{\"email\":\"a@b.com\",\"resetToken\":\"9f8a1c77e0b2")).isEqualTo("***");
        assertThat(AuditValueMasker.mask("{\"a\":1} trailing {\"password\":\"leak4\"}")).isEqualTo("***");
        assertThat(AuditValueMasker.mask("[1,2")).isEqualTo("***");
    }

    @Test
    void keyNamesAreMatchedByTokenRegardlessOfCase() {
        String masked = AuditValueMasker.mask(
                "{\"PASSWORD\":\"c1\",\"new_password\":\"c2\",\"new password\":\"c3\",\"Authorization\":\"c4\",\"otp\":\"c5\","
                        + "\"cvv\":\"c6\",\"cardNumber\":\"c7\",\"cuit\":\"c8\",\"pin\":\"c9\",\"dni\":\"d1\","
                        + "\"verification_code\":481516,\"resetToken\":\"d2\"}");

        assertThat(masked).doesNotContain("c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8", "c9", "d1", "481516", "d2");
    }

    @Test
    void nonSensitiveFieldsStayIntact() {
        String json = "{\"zipcode\":\"1900\",\"statusCode\":\"OK\",\"pinned\":true,\"name\":\"Ana\",\"list\":[1,\"x\"],\"token\":null}";

        assertThat(AuditValueMasker.mask(json)).isEqualTo(json);
        assertThat(AuditValueMasker.mask("ACTIVE")).isEqualTo("ACTIVE");
        assertThat(AuditValueMasker.mask("Se liberó un cupo")).isEqualTo("Se liberó un cupo");
    }

    @Test
    void plainTextWithSensitiveKeyIsFullyMasked() {
        assertThat(AuditValueMasker.mask("password=hunter2 estado=ACTIVO")).isEqualTo("***");
        assertThat(AuditValueMasker.mask("token: abc123")).isEqualTo("***");
    }

    @Test
    void nullAndEmptyAreReturnedAsTheyAre() {
        assertThat(AuditValueMasker.mask(null)).isNull();
        assertThat(AuditValueMasker.mask("")).isEmpty();
    }
}
