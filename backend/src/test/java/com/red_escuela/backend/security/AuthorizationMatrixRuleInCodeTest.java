package com.red_escuela.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationMatrixRuleInCodeTest extends AuthorizationMatrixSupport {

    private static final Set<String> RESTRICTED_WITHOUT_HANDLER_ANNOTATION = Set.of(
            "GET /annual-upfront-terms/current",
            "GET /annual-upfront-terms/versions/{versionNumber}",
            "GET /me/annual-upfront-requests",
            "GET /me/annual-upfront-requests/{id}",
            "GET /me/program-enrollments/{id}/annual-upfront-requests",
            "GET /me/program-enrollments/{id}/annual-upfront/quote");

    @Test
    void everyRestrictedMatrixRowHasAnAuthorizationRuleOnItsHandlerOrIsKnownToEnforceItDownstream() {
        Map<String, AuthorizationMatrixRow> rowsByKey = new HashMap<>();
        AuthorizationMatrixFile.read().forEach(row -> rowsByKey.put(row.key(), row));

        Set<String> restrictedWithoutRule = new TreeSet<>();
        for (MappedEndpoint endpoint : mappedEndpoints()) {
            AuthorizationMatrixRow row = rowsByKey.get(endpoint.key());
            if (row != null && deniesAnyStaffOrStudentRole(row) && !hasPreAuthorize(endpoint)
                    && !endpoint.path().startsWith("/platform/")) {
                restrictedWithoutRule.add(endpoint.key());
            }
        }

        assertThat(restrictedWithoutRule)
                .as("filas restringidas sin @PreAuthorize en el handler")
                .isEqualTo(RESTRICTED_WITHOUT_HANDLER_ANNOTATION);
    }

    @Test
    void publicRowHandlersHaveNoPreAuthorize() {
        List<String> publicRows = AuthorizationMatrixFile.read().stream()
                .filter(row -> row.anonymous() == AccessExpectation.PUBLIC)
                .map(AuthorizationMatrixRow::key)
                .toList();
        List<String> publicWithPreAuthorize = mappedEndpoints().stream()
                .filter(endpoint -> publicRows.contains(endpoint.key()))
                .filter(this::hasPreAuthorize)
                .map(MappedEndpoint::key)
                .toList();

        assertThat(publicWithPreAuthorize).as("fila PUBLIC cuyo handler exige una regla").isEmpty();
    }

    private boolean deniesAnyStaffOrStudentRole(AuthorizationMatrixRow row) {
        if (row.anonymous() == AccessExpectation.PUBLIC) {
            return false;
        }
        return row.byRole().entrySet().stream()
                .anyMatch(entry -> entry.getKey() != MatrixRole.PLATFORM_ADMIN
                        && entry.getValue() == AccessExpectation.DENY);
    }

    private boolean hasPreAuthorize(MappedEndpoint endpoint) {
        return AnnotatedElementUtils.findMergedAnnotation(endpoint.handler().getMethod(), PreAuthorize.class) != null
                || AnnotatedElementUtils.findMergedAnnotation(endpoint.handler().getBeanType(), PreAuthorize.class) != null;
    }
}
