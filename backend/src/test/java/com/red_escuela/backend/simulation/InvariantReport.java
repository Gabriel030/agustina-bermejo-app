package com.red_escuela.backend.simulation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class InvariantReport {

    private final long seed;
    private final int dailyChecks;
    private final List<InvariantViolation> violations;

    public InvariantReport(long seed, int dailyChecks, List<InvariantViolation> violations) {
        this.seed = seed;
        this.dailyChecks = dailyChecks;
        this.violations = violations.stream()
                .sorted(Comparator.comparing(InvariantViolation::detectedOn).thenComparing(InvariantViolation::rule).thenComparing(InvariantViolation::key))
                .toList();
    }

    public List<InvariantViolation> violations() {
        return violations;
    }

    public List<InvariantViolation> violationsOf(String rule) {
        return violations.stream().filter(violation -> violation.rule().equals(rule)).toList();
    }

    public Map<String, Long> countsByRule() {
        Map<String, Long> counts = new TreeMap<>();
        AcademicInvariants.codes().forEach(code -> counts.put(code, 0L));
        violations.forEach(violation -> counts.merge(violation.rule(), 1L, Long::sum));
        return counts;
    }

    public String digest() {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(("seed=" + seed + ";dailyChecks=" + dailyChecks + "\n").getBytes(StandardCharsets.UTF_8));
            violations.forEach(violation -> sha.update((violation.detectedOn() + "|" + violation.rule() + "|" + violation.factDay() + "|" + violation.studentId() + "|" + stableDetail(violation.detail()) + "\n").getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String stableDetail(String detail) {
        return detail.replaceAll("(?<=[a-z_]) \\d+\\b", " #");
    }

    public String toJson() {
        StringBuilder json = new StringBuilder("{\n");
        json.append("  \"seed\": ").append(seed).append(",\n");
        json.append("  \"dailyChecks\": ").append(dailyChecks).append(",\n");
        json.append("  \"digest\": \"").append(digest()).append("\",\n");
        json.append("  \"violationCount\": ").append(violations.size()).append(",\n");
        json.append("  \"countsByRule\": {");
        json.append(countsByRule().entrySet().stream().map(entry -> "\"" + entry.getKey() + "\": " + entry.getValue()).collect(Collectors.joining(", ")));
        json.append("},\n");
        json.append("  \"violations\": [");
        json.append(violations.stream().map(InvariantReport::violationJson).collect(Collectors.joining(",\n    ", violations.isEmpty() ? "" : "\n    ", violations.isEmpty() ? "" : "\n  ")));
        json.append("]\n}\n");
        return json.toString();
    }

    public String toConsole() {
        StringBuilder text = new StringBuilder("invariants: ").append(dailyChecks).append(" daily checks, ")
                .append(violations.size()).append(" violations, digest ").append(digest()).append('\n');
        countsByRule().forEach((rule, count) -> text.append(String.format("  %-45s %d%n", rule, count)));
        violations.stream().limit(40).forEach(violation -> text.append("  [").append(violation.detectedOn()).append("] ")
                .append(violation.rule()).append(" student=").append(violation.studentId()).append(' ').append(violation.detail()).append('\n'));
        return text.toString();
    }

    private static String violationJson(InvariantViolation violation) {
        return "{\"rule\": \"" + violation.rule() + "\", \"simulatedDay\": \"" + violation.detectedOn() + "\", \"factDay\": "
                + (violation.factDay() == null ? "null" : "\"" + violation.factDay() + "\"") + ", \"studentId\": " + violation.studentId()
                + ", \"detail\": \"" + violation.detail().replace("\\", "/").replace("\"", "'") + "\"}";
    }
}
