package com.red_escuela.backend.architecture.modulith;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ViolationSnapshotTest {

    private static String nonExposed(String source, String target, String type) {
        return "Module '" + source + "' depends on non-exposed type com.red_escuela.backend." + target
                + ".repository." + type + " within module '" + target + "'!\nField <x.y." + source
                + "Service.repo> has type <" + type + "> in (" + source + "Service.java:42)";
    }

    private static ViolationSnapshot snapshot(List<String> messages) {
        return ViolationSnapshot.of(messages, Map.of());
    }

    private static ViolationSnapshot snapshotWithDependencies(Map<String, Set<String>> dependencies) {
        return ViolationSnapshot.of(List.of(), dependencies);
    }

    @Test
    void identicalViolationsProduceNoDifference() {
        List<String> messages = List.of(nonExposed("attendance", "identity", "PersonRepository"));

        ViolationSnapshot.Comparison comparison =
                snapshot(messages).compareTo(snapshot(messages));

        assertThat(comparison.added()).isEmpty();
        assertThat(comparison.stale()).isEmpty();
    }

    @Test
    void lineNumbersDoNotChangeTheViolationText() {
        ViolationSnapshot before = snapshot(List.of(nonExposed("attendance", "identity", "PersonRepository")));
        String moved = nonExposed("attendance", "identity", "PersonRepository").replace(":42)", ":99)");

        assertThat(snapshot(List.of(moved)).compareTo(before).hasNewViolations()).isFalse();
    }

    @Test
    void newViolationInStrictModuleIsReported() {
        ViolationSnapshot baseline = snapshot(List.of(nonExposed("attendance", "identity", "PersonRepository")));
        ViolationSnapshot current = snapshot(List.of(
                nonExposed("attendance", "identity", "PersonRepository"),
                nonExposed("attendance", "identity", "StudentRepository")));

        ViolationSnapshot.Comparison comparison = current.compareTo(baseline);

        assertThat(comparison.hasNewViolations()).isTrue();
        assertThat(comparison.added()).singleElement().asString().contains("StudentRepository");
    }

    @Test
    void removedViolationPassesAndIsReportedAsStale() {
        ViolationSnapshot baseline = snapshot(List.of(
                nonExposed("attendance", "identity", "PersonRepository"),
                nonExposed("attendance", "identity", "StudentRepository")));
        ViolationSnapshot current = snapshot(List.of(nonExposed("attendance", "identity", "PersonRepository")));

        ViolationSnapshot.Comparison comparison = current.compareTo(baseline);

        assertThat(comparison.hasNewViolations()).isFalse();
        assertThat(comparison.stale()).singleElement().asString().contains("StudentRepository");
    }

    @Test
    void looseModulesCompareByCountPerTargetModule() {
        ViolationSnapshot baseline = snapshot(List.of(
                nonExposed("billing", "enrollment", "A"), nonExposed("billing", "enrollment", "B")));
        ViolationSnapshot renamed = snapshot(List.of(
                nonExposed("billing", "enrollment", "C"), nonExposed("billing", "enrollment", "D")));
        ViolationSnapshot grown = snapshot(List.of(
                nonExposed("billing", "enrollment", "C"), nonExposed("billing", "enrollment", "D"),
                nonExposed("billing", "enrollment", "E")));

        assertThat(renamed.compareTo(baseline).hasNewViolations()).isFalse();
        assertThat(grown.compareTo(baseline).added()).singleElement().asString()
                .contains("billing -> enrollment went from 2 to 3");
    }

    @Test
    void countsViolationsTowardsLooseModulesEvenWhenSourceIsStrict() {
        ViolationSnapshot baseline = snapshot(List.of(nonExposed("graduation", "billing", "A")));
        ViolationSnapshot renamed = snapshot(List.of(nonExposed("graduation", "billing", "B")));

        assertThat(renamed.compareTo(baseline).hasNewViolations()).isFalse();
    }

    @Test
    void newCycleBetweenModulesIsReported() {
        ViolationSnapshot baseline = snapshotWithDependencies(
                Map.of("billing", Set.of("enrollment"), "enrollment", Set.of("billing")));
        ViolationSnapshot current = snapshotWithDependencies(Map.of(
                "billing", Set.of("enrollment"), "enrollment", Set.of("billing"),
                "attendance", Set.of("evaluation"), "evaluation", Set.of("attendance")));

        assertThat(current.cyclePairs()).containsExactly("attendance<->evaluation", "billing<->enrollment");
        assertThat(current.compareTo(baseline).added()).singleElement().asString()
                .contains("attendance<->evaluation");
    }

    @Test
    void transitiveCycleLinksEveryModuleInIt() {
        ViolationSnapshot snapshot = snapshotWithDependencies(Map.of(
                "a", Set.of("b"), "b", Set.of("c"), "c", Set.of("a"), "d", Set.of("a")));

        assertThat(snapshot.cyclePairs()).containsExactly("a<->b", "a<->c", "b<->c");
    }

    @Test
    void acyclicDependenciesProduceNoCyclePairs() {
        assertThat(snapshotWithDependencies(Map.of("a", Set.of("b"), "b", Set.of("c"))).cyclePairs()).isEmpty();
    }

    @Test
    void snapshotRoundTripsThroughItsFactory() {
        ViolationSnapshot snapshot = ViolationSnapshot.of(Map.of("attendance", List.of("line")), List.of("a<->b"));

        assertThat(snapshot.linesBySourceModule()).containsEntry("attendance", List.of("line"));
        assertThat(snapshot.cyclePairs()).containsExactly("a<->b");
    }
}
