package com.red_escuela.backend.security;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class AccessExpectationTest {

    @Test
    void deniedCellAnsweringBadRequestFailsAsInconclusive() {
        assertThat(AccessExpectation.DENY.describeMismatch(400, false, AccessExpectation.Tolerance.NONE))
                .hasValueSatisfying(message -> assertThat(message).contains("no concluyente"));
    }

    @Test
    void deniedCellAcceptsForbiddenAndAnonymousUnauthorizedOnly() {
        assertThat(AccessExpectation.DENY.describeMismatch(403, false, AccessExpectation.Tolerance.NONE)).isEmpty();
        assertThat(AccessExpectation.DENY.describeMismatch(401, true, AccessExpectation.Tolerance.NONE)).isEmpty();
        assertThat(AccessExpectation.DENY.describeMismatch(401, false, AccessExpectation.Tolerance.NONE)).isPresent();
        assertThat(AccessExpectation.DENY.describeMismatch(200, false, AccessExpectation.Tolerance.NONE)).isPresent();
        assertThat(AccessExpectation.DENY.describeMismatch(404, true, AccessExpectation.Tolerance.ABSENT_RESOURCE)).isPresent();
        assertThat(AccessExpectation.DENY.describeMismatch(500, false, AccessExpectation.Tolerance.ABSENT_RESOURCE)).isPresent();
    }

    @Test
    void allowedPublicAndOwnerCellsFailOnForbidden() {
        assertThat(AccessExpectation.ALLOW.describeMismatch(403, false, AccessExpectation.Tolerance.ABSENT_RESOURCE)).isPresent();
        assertThat(AccessExpectation.PUBLIC.describeMismatch(403, true, AccessExpectation.Tolerance.ABSENT_RESOURCE)).isPresent();
        assertThat(AccessExpectation.OWNER.describeMismatch(403, false, AccessExpectation.Tolerance.ABSENT_RESOURCE)).isPresent();
    }

    @Test
    void allowedPublicAndOwnerCellsFailOnServerErrors() {
        for (AccessExpectation expectation : List.of(AccessExpectation.ALLOW, AccessExpectation.PUBLIC, AccessExpectation.OWNER)) {
            assertThat(expectation.describeMismatch(500, false, AccessExpectation.Tolerance.ABSENT_RESOURCE)).as(expectation.name()).isPresent();
            assertThat(expectation.describeMismatch(503, false, AccessExpectation.Tolerance.NONE)).as(expectation.name()).isPresent();
        }
    }

    @Test
    void allowedCellFailsOnNotFoundUnlessTheResourceMayBeAbsent() {
        assertThat(AccessExpectation.ALLOW.describeMismatch(404, false, AccessExpectation.Tolerance.NONE)).isPresent();
        assertThat(AccessExpectation.PUBLIC.describeMismatch(404, true, AccessExpectation.Tolerance.NONE)).isPresent();
        assertThat(AccessExpectation.OWNER.describeMismatch(404, false, AccessExpectation.Tolerance.NONE)).isPresent();
        assertThat(AccessExpectation.ALLOW.describeMismatch(404, false, AccessExpectation.Tolerance.ABSENT_RESOURCE)).isEmpty();
    }

    @Test
    void allowedCellFailsOnUnauthorizedButPublicCellAcceptsIt() {
        assertThat(AccessExpectation.ALLOW.describeMismatch(401, false, AccessExpectation.Tolerance.NONE)).isPresent();
        assertThat(AccessExpectation.OWNER.describeMismatch(401, false, AccessExpectation.Tolerance.NONE)).isPresent();
        assertThat(AccessExpectation.PUBLIC.describeMismatch(401, true, AccessExpectation.Tolerance.NONE)).isEmpty();
    }

    @Test
    void unauthorizedIsAcceptedOnlyWhenTheToleranceSaysSo() {
        AccessExpectation.Tolerance tolerance = new AccessExpectation.Tolerance(false, true);
        assertThat(AccessExpectation.ALLOW.describeMismatch(401, false, tolerance)).isEmpty();
        assertThat(AccessExpectation.ALLOW.describeMismatch(404, false, tolerance)).isPresent();
        assertThat(AccessExpectation.ALLOW.describeMismatch(500, false, tolerance)).isPresent();
    }

    @Test
    void allowedCellsAcceptSuccessAndValidationStatuses() {
        for (int status : new int[] {200, 201, 204, 400, 409, 422}) {
            assertThat(AccessExpectation.ALLOW.describeMismatch(status, false, AccessExpectation.Tolerance.NONE)).as("" + status).isEmpty();
        }
    }

    @Test
    void matrixFileKeepsJsonBodiesWithCommasAndQuotes() {
        List<String> lines = List.of(
                "method,path,anonymous,ALUMNO,DOCENTE,PRECEPTOR,ADMINISTRATIVO,DIRECTOR,PLATFORM_ADMIN,ownership,body",
                "POST,/foo,DENY,DENY,DENY,ALLOW,ALLOW,DENY,DENY,program-enrollment,\"{\"\"a\"\":1,\"\"b\"\":\"\"x,y\"\"}\"");

        AuthorizationMatrixRow row = AuthorizationMatrixFile.parse(lines).get(0);

        assertThat(row.key()).isEqualTo("POST /foo");
        assertThat(row.anonymous()).isEqualTo(AccessExpectation.DENY);
        assertThat(row.byRole().get(MatrixRole.PRECEPTOR)).isEqualTo(AccessExpectation.ALLOW);
        assertThat(row.ownership()).isEqualTo("program-enrollment");
        assertThat(row.body()).isEqualTo("{\"a\":1,\"b\":\"x,y\"}");
    }
}
