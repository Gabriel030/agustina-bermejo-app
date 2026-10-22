package com.red_escuela.backend.testsupport;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExplorationTierTest {

    @Test
    void missingOrBlankValueIsTheLightTier() {
        assertThat(ExplorationTier.parse(null)).isEqualTo(ExplorationTier.PR);
        assertThat(ExplorationTier.parse(" ")).isEqualTo(ExplorationTier.PR);
        assertThat(ExplorationTier.parse("pr")).isEqualTo(ExplorationTier.PR);
    }

    @Test
    void nightlyValueSelectsTheHeavyTierIgnoringCase() {
        assertThat(ExplorationTier.parse("nightly")).isEqualTo(ExplorationTier.NIGHTLY);
        assertThat(ExplorationTier.parse("NIGHTLY")).isEqualTo(ExplorationTier.NIGHTLY);
    }

    @Test
    void unknownValueFailsInsteadOfFallingBackToTheLightTier() {
        assertThatThrownBy(() -> ExplorationTier.parse("nigthly")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nigthly");
    }
}
