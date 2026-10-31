package com.red_escuela.backend.security;

import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-484")
@SlowTest(reason = "una request MockMvc por endpoint de la aplicación contra el contexto completo")
class PlatformAdminIsolationTest extends AuthorizationMatrixSupport {

    @Test
    void platformAdminIsForbiddenOnEveryRouteOutsideItsOwn() throws Exception {
        List<AuthorizationMatrixRow> rows = AuthorizationMatrixFile.read();
        Set<String> publicKeys = rows.stream()
                .filter(row -> row.anonymous() == AccessExpectation.PUBLIC)
                .map(AuthorizationMatrixRow::key)
                .collect(Collectors.toSet());
        Map<String, String> bodiesByKey = rows.stream()
                .collect(Collectors.toMap(AuthorizationMatrixRow::key, AuthorizationMatrixRow::body, (first, second) -> first));
        List<String> notForbidden = new ArrayList<>();
        int checked = 0;
        for (MappedEndpoint endpoint : mappedEndpoints()) {
            if (publicKeys.contains(endpoint.key()) || isPlatformAdminRoute(endpoint.path())) {
                continue;
            }
            checked++;
            int status = statusOf(endpoint, bodiesByKey.getOrDefault(endpoint.key(), ""), MatrixRole.PLATFORM_ADMIN);
            if (status != 403) {
                notForbidden.add(endpoint.key() + " -> " + status);
            }
        }

        assertThat(checked).isGreaterThan(300);
        assertThat(notForbidden).isEmpty();
    }

    @Test
    void matrixNeverAllowsPlatformAdminOutsideItsOwnRoutes() {
        List<String> allowed = AuthorizationMatrixFile.read().stream()
                .filter(row -> row.anonymous() != AccessExpectation.PUBLIC)
                .filter(row -> row.byRole().get(MatrixRole.PLATFORM_ADMIN) == AccessExpectation.ALLOW)
                .map(AuthorizationMatrixRow::key)
                .filter(key -> !isPlatformAdminRoute(key.substring(key.indexOf(' ') + 1)))
                .toList();

        assertThat(allowed).isEmpty();
    }

    private static boolean isPlatformAdminRoute(String path) {
        return path.equals("/platform") || path.startsWith("/platform/") || path.equals("/me/modules");
    }
}
