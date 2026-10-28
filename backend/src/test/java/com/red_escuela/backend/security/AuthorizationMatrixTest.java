package com.red_escuela.backend.security;

import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "2256 requests MockMvc, una por celda de la matriz, contra el contexto completo")
class AuthorizationMatrixTest extends AuthorizationMatrixSupport {

    record MatrixCell(String key, MatrixRole role, AccessExpectation expected, String body) {

        @Override
        public String toString() {
            return (role == null ? "ANONYMOUS" : role.name()) + " " + key + " -> " + expected;
        }
    }

    static Stream<MatrixCell> matrixCells() {
        List<MatrixCell> cells = new ArrayList<>();
        for (AuthorizationMatrixRow row : AuthorizationMatrixFile.read()) {
            cells.add(new MatrixCell(row.key(), null, row.anonymous(), row.body()));
            for (MatrixRole role : MatrixRole.values()) {
                cells.add(new MatrixCell(row.key(), role, row.byRole().get(role), row.body()));
            }
        }
        return cells.stream();
    }

    private Map<String, MappedEndpoint> endpointsByKey;

    private Map<String, MappedEndpoint> endpointsByKey() {
        if (endpointsByKey == null) {
            endpointsByKey = mappedEndpoints().stream()
                    .collect(Collectors.toMap(MappedEndpoint::key, Function.identity(), (first, second) -> first));
        }
        return endpointsByKey;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matrixCells")
    void roleAccessMatchesMatrix(MatrixCell cell) throws Exception {
        MappedEndpoint endpoint = endpointsByKey().get(cell.key());
        assertThat(endpoint).as("mapping para %s", cell.key()).isNotNull();

        int status = statusOf(endpoint, cell.body(), cell.role());

        assertThat(cell.expected().describeMismatch(status, cell.role() == null))
                .as("%s", cell)
                .isEmpty();
    }
}
