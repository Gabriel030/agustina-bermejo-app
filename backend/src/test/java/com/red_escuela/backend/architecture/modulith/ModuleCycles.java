package com.red_escuela.backend.architecture.modulith;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class ModuleCycles {

    private ModuleCycles() {
    }

    public static List<String> pairsOf(Map<String, Set<String>> dependencies) {
        Set<String> nodes = new TreeSet<>(dependencies.keySet());
        dependencies.values().forEach(nodes::addAll);
        Set<String> pairs = new TreeSet<>();
        for (String left : nodes) {
            Set<String> reachableFromLeft = reachableFrom(left, dependencies);
            for (String right : nodes) {
                if (left.compareTo(right) < 0
                        && reachableFromLeft.contains(right)
                        && reachableFrom(right, dependencies).contains(left)) {
                    pairs.add(left + "<->" + right);
                }
            }
        }
        return new ArrayList<>(pairs);
    }

    private static Set<String> reachableFrom(String start, Map<String, Set<String>> dependencies) {
        Set<String> visited = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>(dependencies.getOrDefault(start, Set.of()));
        while (!pending.isEmpty()) {
            String current = pending.pop();
            if (visited.add(current)) {
                pending.addAll(dependencies.getOrDefault(current, Set.of()));
            }
        }
        return visited;
    }
}
