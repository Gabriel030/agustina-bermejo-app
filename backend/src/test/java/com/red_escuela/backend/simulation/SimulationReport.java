package com.red_escuela.backend.simulation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class SimulationReport {

    private final long seed;
    private final int year;
    private final List<String> events = new ArrayList<>();
    private final Map<String, Integer> counters = new TreeMap<>();
    private final List<String> outcomes = new ArrayList<>();
    private int simulatedDays;
    private long elapsedMillis;

    public SimulationReport(long seed, int year) {
        this.seed = seed;
        this.year = year;
    }

    public void recordEvent(String event) {
        events.add(event);
    }

    public void count(String counter) {
        counters.merge(counter, 1, Integer::sum);
    }

    public void recordOutcome(String outcome) {
        outcomes.add(outcome);
    }

    public void finish(int simulatedDays, long elapsedMillis) {
        this.simulatedDays = simulatedDays;
        this.elapsedMillis = elapsedMillis;
    }

    public int simulatedDays() {
        return simulatedDays;
    }

    public long elapsedMillis() {
        return elapsedMillis;
    }

    public int counter(String counter) {
        return counters.getOrDefault(counter, 0);
    }

    public List<String> events() {
        return List.copyOf(events);
    }

    public List<String> outcomes() {
        return List.copyOf(outcomes);
    }

    public String digest() {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(("seed=" + seed + ";year=" + year + ";days=" + simulatedDays + "\n").getBytes(StandardCharsets.UTF_8));
            events.forEach(event -> sha.update((event + "\n").getBytes(StandardCharsets.UTF_8)));
            counters.forEach((name, value) -> sha.update((name + "=" + value + "\n").getBytes(StandardCharsets.UTF_8)));
            outcomes.forEach(outcome -> sha.update((outcome + "\n").getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    public String toJson() {
        StringBuilder json = new StringBuilder("{\n");
        json.append("  \"seed\": ").append(seed).append(",\n");
        json.append("  \"year\": ").append(year).append(",\n");
        json.append("  \"simulatedDays\": ").append(simulatedDays).append(",\n");
        json.append("  \"elapsedMillis\": ").append(elapsedMillis).append(",\n");
        json.append("  \"digest\": \"").append(digest()).append("\",\n");
        json.append("  \"counters\": {");
        json.append(String.join(", ", counters.entrySet().stream().map(entry -> "\"" + entry.getKey() + "\": " + entry.getValue()).toList()));
        json.append("},\n");
        json.append("  \"eventCount\": ").append(events.size()).append(",\n");
        json.append("  \"outcomes\": [");
        json.append(String.join(", ", outcomes.stream().map(outcome -> "\"" + outcome.replace("\"", "'") + "\"").toList()));
        json.append("]\n}\n");
        return json.toString();
    }
}
