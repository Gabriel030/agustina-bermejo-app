package com.red_escuela.backend.billing.decision;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DecisionRegistry {

    private static final int COLUMN_COUNT = 7;
    private static final Pattern ID = Pattern.compile("D-\\d{3}");
    private static final Pattern SUPERSEDED = Pattern.compile("(reemplazada|resuelta) por (D-\\d{3}(, D-\\d{3})*)");
    private static final Pattern CONFIGURABLE = Pattern.compile("no|sí|sí \\(.+\\)");
    private static final Pattern PHASED = Pattern.compile("(parcial|pendiente) \\(.+\\)");
    private static final Pattern VIOLATED = Pattern.compile("violada \\(.+\\): \\S.*");
    private static final Pattern SPLIT_ON_UNESCAPED_PIPE = Pattern.compile("(?<!\\\\)\\|");

    private final Map<String, DecisionRegistryRow> rows;

    private DecisionRegistry(Map<String, DecisionRegistryRow> rows) {
        this.rows = rows;
    }

    public static DecisionRegistry readFile(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Decision registry not found at " + path.toAbsolutePath().normalize());
        }
        try {
            return parse(Files.readString(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static DecisionRegistry parse(String content) {
        List<String> errors = new ArrayList<>();
        Map<String, DecisionRegistryRow> parsed = new LinkedHashMap<>();
        for (String line : content.split("\n")) {
            if (!line.startsWith("| D-")) {
                continue;
            }
            parseRow(line, parsed, errors);
        }
        validateReferences(parsed, errors);
        if (!errors.isEmpty()) {
            throw new InvalidDecisionRegistryException(errors);
        }
        return new DecisionRegistry(parsed);
    }

    private static void parseRow(String line, Map<String, DecisionRegistryRow> parsed, List<String> errors) {
        String[] raw = SPLIT_ON_UNESCAPED_PIPE.split(line.strip(), -1);
        String idHint = raw.length > 1 ? raw[1].strip() : "?";
        if (raw.length != COLUMN_COUNT + 2) {
            errors.add(idHint + " expected " + COLUMN_COUNT + " columns but found " + (raw.length - 2));
            return;
        }
        String[] cells = new String[COLUMN_COUNT];
        for (int i = 0; i < COLUMN_COUNT; i++) {
            cells[i] = raw[i + 1].strip().replace("\\|", "|");
        }
        DecisionRegistryRow row = new DecisionRegistryRow(cells[0], cells[1], cells[2], cells[3], cells[4], cells[5], cells[6]);
        int before = errors.size();
        if (!ID.matcher(row.id()).matches()) {
            errors.add(row.id() + " column ID: must match D-nnn");
        }
        if (parsed.containsKey(row.id())) {
            errors.add(row.id() + " column ID: duplicated");
        }
        if (!isValidStatus(row.status())) {
            errors.add(row.id() + " column Estado: invalid value '" + row.status() + "'");
        }
        if (!CONFIGURABLE.matcher(row.configurable()).matches()) {
            errors.add(row.id() + " column Configurable: invalid value '" + row.configurable() + "'");
        }
        if (!isValidImplementation(row.implementation())) {
            errors.add(row.id() + " column Implementación: invalid value '" + row.implementation() + "'");
        }
        if (row.isCurrent() && !row.isNotApplicable() && isBlankCase(row.testCase())) {
            errors.add(row.id() + " column Caso de test: required for a current decision that applies");
        }
        if (errors.size() == before) {
            parsed.put(row.id(), row);
        }
    }

    private static boolean isBlankCase(String testCase) {
        return testCase.isBlank() || testCase.equals("—");
    }

    private static boolean isValidStatus(String status) {
        return status.equals("vigente") || status.equals("a confirmar") || status.equals("pendiente")
                || SUPERSEDED.matcher(status).matches();
    }

    private static boolean isValidImplementation(String implementation) {
        return implementation.equals("implementada") || implementation.equals("no aplica")
                || PHASED.matcher(implementation).matches() || VIOLATED.matcher(implementation).matches();
    }

    private static void validateReferences(Map<String, DecisionRegistryRow> parsed, List<String> errors) {
        for (DecisionRegistryRow row : parsed.values()) {
            Matcher matcher = SUPERSEDED.matcher(row.status());
            if (!matcher.matches()) {
                continue;
            }
            for (String target : matcher.group(2).split(", ")) {
                if (!parsed.containsKey(target)) {
                    errors.add(row.id() + " column Estado: references " + target + " which does not exist");
                }
            }
        }
    }

    public Optional<DecisionRegistryRow> find(String id) {
        return Optional.ofNullable(rows.get(id));
    }

    public List<DecisionRegistryRow> rows() {
        return List.copyOf(rows.values());
    }

    public int size() {
        return rows.size();
    }
}
