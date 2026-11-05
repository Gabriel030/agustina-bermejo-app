package com.red_escuela.backend.security;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationMatrixCompletenessTest extends AuthorizationMatrixSupport {

    @Test
    void everyHandlerMappingHasExactlyOneMatrixRow() {
        Map<String, Long> rowsByKey = AuthorizationMatrixFile.read().stream()
                .collect(Collectors.groupingBy(AuthorizationMatrixRow::key, Collectors.counting()));
        Map<String, Long> mappingsByKey = mappedEndpoints().stream()
                .collect(Collectors.groupingBy(MappedEndpoint::key, Collectors.counting()));

        Set<String> withoutRow = new TreeSet<>(mappingsByKey.keySet());
        withoutRow.removeAll(rowsByKey.keySet());
        Set<String> duplicated = rowsByKey.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> mappedTwice = mappingsByKey.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(withoutRow).as("endpoint sin fila en la matriz").isEmpty();
        assertThat(duplicated).as("filas duplicadas en la matriz").isEmpty();
        assertThat(mappedTwice).as("mismo metodo y ruta mapeados dos veces").isEmpty();
    }

    @Test
    void everyMatrixRowMatchesAnExistingMapping() {
        Set<String> mappedKeys = mappedEndpoints().stream()
                .collect(Collectors.toMap(MappedEndpoint::key, Function.identity(), (first, second) -> first))
                .keySet();
        List<String> rowsWithoutMapping = AuthorizationMatrixFile.read().stream()
                .map(AuthorizationMatrixRow::key)
                .filter(key -> !mappedKeys.contains(key))
                .sorted()
                .toList();

        assertThat(rowsWithoutMapping).as("fila de la matriz sin mapping existente").isEmpty();
    }

    @Test
    void ownershipColumnAndOwnerCellsAgree() {
        List<String> inconsistent = AuthorizationMatrixFile.read().stream()
                .filter(row -> !ownershipAgreesWithOwnerCells(row))
                .map(AuthorizationMatrixRow::key)
                .sorted()
                .toList();

        assertThat(inconsistent).as("filas con ownership y celdas OWNER que no coinciden").isEmpty();
    }

    private boolean ownershipAgreesWithOwnerCells(AuthorizationMatrixRow row) {
        List<MatrixRole> ownerRoles = row.byRole().entrySet().stream()
                .filter(entry -> entry.getValue() == AccessExpectation.OWNER)
                .map(Map.Entry::getKey)
                .toList();
        if (row.ownership().isEmpty()) {
            return ownerRoles.isEmpty() && row.anonymous() != AccessExpectation.OWNER;
        }
        return ownerRoles.equals(List.of(OwnershipKind.fromLabel(row.ownership()).ownerRole()));
    }
}
