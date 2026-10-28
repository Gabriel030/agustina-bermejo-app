package com.red_escuela.backend.security;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class AccessExpectationTest {

    @Test
    void deniedCellAnsweringBadRequestFailsAsInconclusive() {
        assertThat(AccessExpectation.DENY.describeMismatch(400, false))
                .hasValueSatisfying(message -> assertThat(message).contains("no concluyente"));
    }

    @Test
    void deniedCellAcceptsForbiddenAndAnonymousUnauthorizedOnly() {
        assertThat(AccessExpectation.DENY.describeMismatch(403, false)).isEmpty();
        assertThat(AccessExpectation.DENY.describeMismatch(401, true)).isEmpty();
        assertThat(AccessExpectation.DENY.describeMismatch(401, false)).isPresent();
        assertThat(AccessExpectation.DENY.describeMismatch(200, false)).isPresent();
        assertThat(AccessExpectation.DENY.describeMismatch(404, true)).isPresent();
    }

    @Test
    void allowedAndPublicCellsFailOnlyOnForbidden() {
        assertThat(AccessExpectation.ALLOW.describeMismatch(403, false)).isPresent();
        assertThat(AccessExpectation.PUBLIC.describeMismatch(403, true)).isPresent();
        assertThat(AccessExpectation.ALLOW.describeMismatch(404, false)).isEmpty();
        assertThat(AccessExpectation.ALLOW.describeMismatch(400, false)).isEmpty();
        assertThat(AccessExpectation.PUBLIC.describeMismatch(401, true)).isEmpty();
    }

    @Test
    void matrixFileKeepsJsonBodiesWithCommasAndQuotes() {
        List<String> lines = List.of(
                "method,path,anonymous,ALUMNO,DOCENTE,PRECEPTOR,ADMINISTRATIVO,DIRECTOR,body",
                "POST,/foo,DENY,DENY,DENY,ALLOW,ALLOW,DENY,\"{\"\"a\"\":1,\"\"b\"\":\"\"x,y\"\"}\"");

        AuthorizationMatrixRow row = AuthorizationMatrixFile.parse(lines).get(0);

        assertThat(row.key()).isEqualTo("POST /foo");
        assertThat(row.anonymous()).isEqualTo(AccessExpectation.DENY);
        assertThat(row.byRole().get(MatrixRole.PRECEPTOR)).isEqualTo(AccessExpectation.ALLOW);
        assertThat(row.body()).isEqualTo("{\"a\":1,\"b\":\"x,y\"}");
    }
}
