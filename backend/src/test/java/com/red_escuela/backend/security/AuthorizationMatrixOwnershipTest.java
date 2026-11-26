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

    static Stream<OwnershipCase> administrativeOwnedRows() {
        return ownershipCases().filter(ownershipCase -> ownershipCase.kind().ownerRole() == MatrixRole.ADMINISTRATIVO);
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
    void instructorOfAnotherSectionGets404(OwnershipCase ownershipCase) throws Exception {
        assertOwnerPassesAndStrangerGetsExpectedStatus(ownershipCase);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("administrativeOwnedRows")
    void administrativeWhoDidNotOpenTheRequestGets404(OwnershipCase ownershipCase) throws Exception {
        assertOwnerPassesAndStrangerGetsExpectedStatus(ownershipCase);
    }

    private void assertOwnerPassesAndStrangerGetsExpectedStatus(OwnershipCase ownershipCase) throws Exception {
        OwnershipKind kind = ownershipCase.kind();
        Map<String, MappedEndpoint> endpointsByKey = mappedEndpoints().stream()
                .collect(Collectors.toMap(MappedEndpoint::key, Function.identity(), (first, second) -> first));
        MappedEndpoint endpoint = endpointsByKey.get(ownershipCase.row().key());
        assertThat(endpoint).as("mapping para %s", ownershipCase.row().key()).isNotNull();
        Long resourceId = ownership.resourceIdFor(kind);
        String body = ownership.resolvePlaceholders(ownershipCase.row().body());

        int strangerStatus = statusWithToken(endpoint, body, ownership.strangerTokenFor(kind), resourceId);
        int ownerStatus = statusWithToken(endpoint, body, ownership.ownerTokenFor(kind), resourceId);

        assertThat(ownerStatus).as("dueno en %s %s", ownershipCase, lastResponseBody).isNotIn(403, 404);
        assertThat(strangerStatus).as("no dueno en %s", ownershipCase).isEqualTo(kind.strangerStatus());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("administrativeOwnedRows")
    void subdirectorWhoDidNotOpenTheRequestGetsTheSameStatusAsAnotherAdministrative(OwnershipCase ownershipCase) throws Exception {
        MappedEndpoint endpoint = mappedEndpoints().stream()
                .filter(candidate -> candidate.key().equals(ownershipCase.row().key()))
                .findFirst().orElseThrow();
        String body = ownership.resolvePlaceholders(ownershipCase.row().body());

        int status = statusWithToken(endpoint, body, fixtures.tokenFor(MatrixRole.SUBDIRECTOR), ownership.resourceIdFor(ownershipCase.kind()));

        assertThat(status).as("subdirector no dueno en %s", ownershipCase).isEqualTo(ownershipCase.kind().strangerStatus());
    }
}
