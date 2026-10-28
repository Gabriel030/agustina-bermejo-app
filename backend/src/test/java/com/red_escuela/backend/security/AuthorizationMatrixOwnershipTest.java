package com.red_escuela.backend.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationMatrixOwnershipTest extends AuthorizationMatrixSupport {

    record OwnershipCase(AuthorizationMatrixRow row, OwnershipKind kind) {

        @Override
        public String toString() {
            return kind.label() + " " + row.key();
        }
    }

    static Stream<OwnershipCase> studentOwnedRows() {
        return ownershipCases().filter(ownershipCase -> ownershipCase.kind().ownerRole() == MatrixRole.ALUMNO);
    }

    static Stream<OwnershipCase> instructorOwnedRows() {
        return ownershipCases().filter(ownershipCase -> ownershipCase.kind().ownerRole() == MatrixRole.DOCENTE);
    }

    private static Stream<OwnershipCase> ownershipCases() {
        return AuthorizationMatrixFile.read().stream()
                .filter(row -> !row.ownership().isEmpty())
                .map(row -> new OwnershipCase(row, OwnershipKind.fromLabel(row.ownership())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("studentOwnedRows")
    void ownerCanAccessAndNonOwnerGets404(OwnershipCase ownershipCase) throws Exception {
        assertOwnerPassesAndStrangerGetsExpectedStatus(ownershipCase);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("instructorOwnedRows")
    void instructorOfAnotherSectionGets403(OwnershipCase ownershipCase) throws Exception {
        assertOwnerPassesAndStrangerGetsExpectedStatus(ownershipCase);
    }

    private void assertOwnerPassesAndStrangerGetsExpectedStatus(OwnershipCase ownershipCase) throws Exception {
        OwnershipKind kind = ownershipCase.kind();
        Map<String, MappedEndpoint> endpointsByKey = mappedEndpoints().stream()
                .collect(Collectors.toMap(MappedEndpoint::key, Function.identity(), (first, second) -> first));
        MappedEndpoint endpoint = endpointsByKey.get(ownershipCase.row().key());
        assertThat(endpoint).as("mapping para %s", ownershipCase.row().key()).isNotNull();
        Long resourceId = ownership.resourceIdFor(kind);

        int ownerStatus = statusWithToken(endpoint, ownershipCase.row().body(), ownership.ownerTokenFor(kind), resourceId);
        int strangerStatus = statusWithToken(endpoint, ownershipCase.row().body(), ownership.strangerTokenFor(kind), resourceId);

        assertThat(ownerStatus).as("dueno en %s", ownershipCase).isNotIn(403, 404);
        assertThat(strangerStatus).as("no dueno en %s", ownershipCase).isEqualTo(kind.strangerStatus());
    }
}
