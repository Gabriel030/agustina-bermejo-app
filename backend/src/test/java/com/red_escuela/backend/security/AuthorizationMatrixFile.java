package com.red_escuela.backend.security;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class AuthorizationMatrixFile {

    static final Path LOCATION = Path.of("src", "test", "resources", "security", "authorization-matrix.csv");

    private static final String HEADER = "method,path,anonymous,"
            + Stream.of(MatrixRole.values()).map(Enum::name).collect(Collectors.joining(","))
            + ",ownership,body";

    private AuthorizationMatrixFile() {
    }

    static List<AuthorizationMatrixRow> read() {
        return parse(readLines());
    }

    static List<AuthorizationMatrixRow> parse(List<String> lines) {
        if (lines.isEmpty() || !HEADER.equals(lines.get(0))) {
            throw new IllegalStateException("Encabezado inesperado en authorization-matrix.csv, se esperaba: " + HEADER);
        }
        List<AuthorizationMatrixRow> rows = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            if (!lines.get(index).isBlank()) {
                rows.add(parseRow(lines.get(index), index + 1));
            }
        }
        return rows;
    }

    static void write(List<AuthorizationMatrixRow> rows) {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (AuthorizationMatrixRow row : rows) {
            StringBuilder line = new StringBuilder();
            line.append(row.method()).append(',').append(row.path()).append(',').append(row.anonymous());
            for (MatrixRole role : MatrixRole.values()) {
                line.append(',').append(row.byRole().get(role));
            }
            line.append(',').append(row.ownership()).append(',').append(quote(row.body()));
            lines.add(line.toString());
        }
        try {
            Files.write(LOCATION, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> readLines() {
        try {
            return Files.readAllLines(LOCATION, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + LOCATION, e);
        }
    }

    private static AuthorizationMatrixRow parseRow(String line, int lineNumber) {
        List<String> cells = splitCells(line);
        int expectedCells = 3 + MatrixRole.values().length + 2;
        if (cells.size() != expectedCells) {
            throw new IllegalStateException("authorization-matrix.csv linea " + lineNumber + ": se esperaban "
                    + expectedCells + " columnas y hay " + cells.size());
        }
        Map<MatrixRole, AccessExpectation> byRole = new EnumMap<>(MatrixRole.class);
        for (MatrixRole role : MatrixRole.values()) {
            byRole.put(role, AccessExpectation.valueOf(cells.get(3 + role.ordinal())));
        }
        return new AuthorizationMatrixRow(
                cells.get(0),
                cells.get(1),
                AccessExpectation.valueOf(cells.get(2)),
                byRole,
                cells.get(expectedCells - 2),
                cells.get(expectedCells - 1));
    }

    private static List<String> splitCells(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int position = 0; position < line.length(); position++) {
            char character = line.charAt(position);
            if (quoted) {
                if (character == '"' && position + 1 < line.length() && line.charAt(position + 1) == '"') {
                    current.append('"');
                    position++;
                } else if (character == '"') {
                    quoted = false;
                } else {
                    current.append(character);
                }
            } else if (character == '"') {
                quoted = true;
            } else if (character == ',') {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    private static String quote(String value) {
        if (value.isEmpty()) {
            return "";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
