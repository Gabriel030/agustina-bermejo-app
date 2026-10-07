package com.red_escuela.backend.architecture.modulith;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ViolationSnapshot {

    public static final Set<String> LOOSE_MODULES = Set.of("billing", "enrollment");
    public static final String COUNT_PREFIX = "COUNT ";

    private static final Pattern SOURCE = Pattern.compile("^Module '(\\w+)'");
    private static final Pattern TARGET = Pattern.compile(
            "within module '(\\w+)'|depends on module '(\\w+)'|named interface\\(s\\) '(\\w+) ::");
    private static final Pattern LINE_NUMBER = Pattern.compile(":\\d+\\)");

    private final Map<String, List<String>> linesBySourceModule;
    private final List<String> cyclePairs;

    private ViolationSnapshot(Map<String, List<String>> linesBySourceModule, List<String> cyclePairs) {
        this.linesBySourceModule = linesBySourceModule;
        this.cyclePairs = cyclePairs;
    }

    public static ViolationSnapshot of(Collection<String> messages, Map<String, Set<String>> moduleDependencies) {
        Map<String, List<String>> exact = new TreeMap<>();
        Map<String, Map<String, Integer>> counts = new TreeMap<>();
        for (String message : messages) {
            if (message.startsWith("Cycle detected")) {
                continue;
            }
            Matcher source = SOURCE.matcher(message);
            if (!source.find()) {
                exact.computeIfAbsent("unclassified", key -> new ArrayList<>()).add(normalize(message));
                continue;
            }
            String sourceModule = source.group(1);
            String targetModule = targetOf(message);
            if (LOOSE_MODULES.contains(sourceModule) || LOOSE_MODULES.contains(targetModule)) {
                counts.computeIfAbsent(sourceModule, key -> new TreeMap<>()).merge(targetModule, 1, Integer::sum);
            } else {
                exact.computeIfAbsent(sourceModule, key -> new ArrayList<>()).add(normalize(message));
            }
        }
        Map<String, List<String>> result = new TreeMap<>();
        exact.forEach((module, lines) -> result.computeIfAbsent(module, key -> new ArrayList<>()).addAll(lines));
        counts.forEach((module, perTarget) -> perTarget.forEach((target, count) ->
                result.computeIfAbsent(module, key -> new ArrayList<>()).add(COUNT_PREFIX + target + " " + count)));
        result.values().forEach(lines -> lines.sort(String::compareTo));
        return new ViolationSnapshot(result, ModuleCycles.pairsOf(moduleDependencies));
    }

    public static ViolationSnapshot of(Map<String, List<String>> linesBySourceModule, List<String> cyclePairs) {
        Map<String, List<String>> copy = new TreeMap<>();
        linesBySourceModule.forEach((module, lines) -> copy.put(module, List.copyOf(lines)));
        return new ViolationSnapshot(copy, List.copyOf(cyclePairs));
    }

    public Map<String, List<String>> linesBySourceModule() {
        return linesBySourceModule;
    }

    public List<String> cyclePairs() {
        return cyclePairs;
    }

    public Comparison compareTo(ViolationSnapshot baseline) {
        List<String> added = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        Set<String> modules = new TreeSet<>(linesBySourceModule.keySet());
        modules.addAll(baseline.linesBySourceModule.keySet());
        for (String module : modules) {
            compareLines(module,
                    linesBySourceModule.getOrDefault(module, List.of()),
                    baseline.linesBySourceModule.getOrDefault(module, List.of()),
                    added, stale);
        }
        Set<String> currentCycles = new TreeSet<>(cyclePairs);
        Set<String> baselineCycles = new TreeSet<>(baseline.cyclePairs);
        currentCycles.stream().filter(pair -> !baselineCycles.contains(pair))
                .forEach(pair -> added.add("cycles: new dependency cycle between " + pair));
        baselineCycles.stream().filter(pair -> !currentCycles.contains(pair))
                .forEach(pair -> stale.add("cycles: " + pair));
        return new Comparison(added, stale);
    }

    private static void compareLines(String module, List<String> current, List<String> baseline,
                                     List<String> added, List<String> stale) {
        Map<String, Integer> currentCounts = countsOf(current);
        Map<String, Integer> baselineCounts = countsOf(baseline);
        Set<String> targets = new TreeSet<>(currentCounts.keySet());
        targets.addAll(baselineCounts.keySet());
        for (String target : targets) {
            int now = currentCounts.getOrDefault(target, 0);
            int before = baselineCounts.getOrDefault(target, 0);
            if (now > before) {
                added.add(module + ": " + module + " -> " + target + " went from " + before + " to " + now
                        + " accesses to non-public types; use the public API (api, events or model) of " + target);
            } else if (now < before) {
                stale.add(module + ": " + COUNT_PREFIX + target + " " + before + " (now " + now + ")");
            }
        }
        Set<String> baselineExact = new TreeSet<>(exactOf(baseline));
        Set<String> currentExact = new TreeSet<>(exactOf(current));
        currentExact.stream().filter(line -> !baselineExact.contains(line))
                .forEach(line -> added.add(module + ": " + line));
        baselineExact.stream().filter(line -> !currentExact.contains(line))
                .forEach(line -> stale.add(module + ": " + line));
    }

    private static Map<String, Integer> countsOf(List<String> lines) {
        Map<String, Integer> counts = new TreeMap<>();
        for (String line : lines) {
            if (line.startsWith(COUNT_PREFIX)) {
                String[] parts = line.substring(COUNT_PREFIX.length()).split(" ");
                counts.put(parts[0], Integer.parseInt(parts[1]));
            }
        }
        return counts;
    }

    private static List<String> exactOf(List<String> lines) {
        return lines.stream().filter(line -> !line.startsWith(COUNT_PREFIX)).toList();
    }

    private static String targetOf(String message) {
        Matcher matcher = TARGET.matcher(message);
        if (!matcher.find()) {
            return "unknown";
        }
        for (int group = 1; group <= matcher.groupCount(); group++) {
            if (matcher.group(group) != null) {
                return matcher.group(group);
            }
        }
        return "unknown";
    }

    private static String normalize(String message) {
        String singleLine = message.replace("\r", "").replaceAll("\\s*\\n\\s*", " | ").trim();
        return LINE_NUMBER.matcher(singleLine).replaceAll(")");
    }

    public record Comparison(List<String> added, List<String> stale) {
        public boolean hasNewViolations() {
            return !added.isEmpty();
        }
    }
}
