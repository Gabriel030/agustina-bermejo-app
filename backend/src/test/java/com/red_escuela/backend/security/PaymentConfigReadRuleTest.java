package com.red_escuela.backend.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentConfigReadRuleTest {

    private static final List<String> READ_ENDPOINTS = List.of("GET /payment-config", "GET /payment-config/qr-image");

    @Test
    void paymentConfigReadsAreVisibleToEveryAuthenticatedRoleExceptPlatformAdmin() {
        List<AuthorizationMatrixRow> rows = AuthorizationMatrixFile.read().stream()
                .filter(row -> READ_ENDPOINTS.contains(row.key()))
                .toList();

        assertThat(rows).hasSize(READ_ENDPOINTS.size());
        for (AuthorizationMatrixRow row : rows) {
            assertThat(row.anonymous()).as(row.key()).isEqualTo(AccessExpectation.DENY);
            for (MatrixRole role : MatrixRole.values()) {
                AccessExpectation expected = role == MatrixRole.PLATFORM_ADMIN ? AccessExpectation.DENY : AccessExpectation.ALLOW;
                assertThat(row.byRole().get(role)).as(row.key() + " " + role).isEqualTo(expected);
            }
        }
    }

    @Test
    void paymentConfigWritesStayOnlyForTheDirector() {
        List<AuthorizationMatrixRow> rows = AuthorizationMatrixFile.read().stream()
                .filter(row -> row.key().equals("PUT /payment-config") || row.key().equals("POST /payment-config/qr"))
                .toList();

        assertThat(rows).hasSize(2);
        for (AuthorizationMatrixRow row : rows) {
            for (MatrixRole role : MatrixRole.values()) {
                AccessExpectation expected = role == MatrixRole.DIRECTOR ? AccessExpectation.ALLOW : AccessExpectation.DENY;
                assertThat(row.byRole().get(role)).as(row.key() + " " + role).isEqualTo(expected);
            }
        }
    }
}
