package com.red_escuela.backend.shared.domain.statemachine;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-051")
class StateTransitionTableTest {

    enum Light { RED, GREEN, BLUE }

    enum Signal { GO, STOP }

    private final StateTransitionTable<Light, Signal> table = StateTransitionTable.forStatesAndEvents(Light.class, Signal.class)
            .allowInitialTransition(Signal.STOP, Light.RED)
            .allowTransition(Light.RED, Signal.GO, Light.GREEN)
            .allowTransition(Light.GREEN, Signal.STOP, Light.RED)
            .buildTransitionTable();

    @Test
    void findsTheTargetOfADeclaredTransition() {
        assertThat(table.findTargetStateFor(Light.RED, Signal.GO)).contains(Light.GREEN);
        assertThat(table.findTargetStateFor(Light.GREEN, Signal.STOP)).contains(Light.RED);
    }

    @Test
    void findsNothingForAnUndeclaredTransition() {
        assertThat(table.findTargetStateFor(Light.RED, Signal.STOP)).isEmpty();
        assertThat(table.findTargetStateFor(Light.BLUE, Signal.GO)).isEmpty();
    }

    @Test
    void theInitialStateComesFromANullOrigin() {
        assertThat(table.findTargetStateFor(null, Signal.STOP)).contains(Light.RED);
        assertThat(table.findTargetStateFor(null, Signal.GO)).isEqualTo(Optional.empty());
    }

    @Test
    void anInitialTransitionIsNotAllowedFromARealState() {
        assertThat(table.findTargetStateFor(Light.BLUE, Signal.STOP)).isEmpty();
    }

    @Test
    void listsEveryAllowedTransitionIncludingTheInitialOnes() {
        assertThat(table.listAllowedTransitions()).containsExactlyInAnyOrder(
                new StateTransitionRow<>(null, Signal.STOP, Light.RED),
                new StateTransitionRow<>(Light.RED, Signal.GO, Light.GREEN),
                new StateTransitionRow<>(Light.GREEN, Signal.STOP, Light.RED));
    }

    @Test
    void theListOfTransitionsCannotBeModified() {
        assertThatThrownBy(() -> table.listAllowedTransitions().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void exposesItsStateAndEventTypes() {
        assertThat(table.stateType()).isEqualTo(Light.class);
        assertThat(table.eventType()).isEqualTo(Signal.class);
    }

    @Test
    void aDuplicatedTransitionIsRejectedWhenBuilding() {
        StateTransitionTable.Builder<Light, Signal> builder = StateTransitionTable.forStatesAndEvents(Light.class, Signal.class)
                .allowTransition(Light.RED, Signal.GO, Light.GREEN)
                .allowTransition(Light.RED, Signal.GO, Light.BLUE);

        assertThatThrownBy(builder::buildTransitionTable).isInstanceOf(IllegalStateException.class).hasMessageContaining("(RED, GO)");
    }

    @Test
    void aDuplicatedInitialTransitionIsRejectedWhenBuilding() {
        StateTransitionTable.Builder<Light, Signal> builder = StateTransitionTable.forStatesAndEvents(Light.class, Signal.class)
                .allowInitialTransition(Signal.GO, Light.RED)
                .allowInitialTransition(Signal.GO, Light.BLUE);

        assertThatThrownBy(builder::buildTransitionTable).isInstanceOf(IllegalStateException.class).hasMessageContaining("(initial, GO)");
    }

    @Test
    void nullArgumentsAreRejected() {
        StateTransitionTable.Builder<Light, Signal> builder = StateTransitionTable.forStatesAndEvents(Light.class, Signal.class);

        assertThatThrownBy(() -> StateTransitionTable.forStatesAndEvents(null, Signal.class)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StateTransitionTable.forStatesAndEvents(Light.class, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> builder.allowInitialTransition(null, Light.RED)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> builder.allowInitialTransition(Signal.GO, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> builder.allowTransition(null, Signal.GO, Light.RED)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> builder.allowTransition(Light.RED, null, Light.RED)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> builder.allowTransition(Light.RED, Signal.GO, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> table.findTargetStateFor(Light.RED, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void theBuiltTableDoesNotChangeWhenTheBuilderIsReused() {
        StateTransitionTable.Builder<Light, Signal> builder = StateTransitionTable.forStatesAndEvents(Light.class, Signal.class)
                .allowTransition(Light.RED, Signal.GO, Light.GREEN);
        StateTransitionTable<Light, Signal> built = builder.buildTransitionTable();
        builder.allowTransition(Light.GREEN, Signal.GO, Light.BLUE);

        assertThat(built.findTargetStateFor(Light.GREEN, Signal.GO)).isEmpty();
    }
}
