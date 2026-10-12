package com.red_escuela.backend.shared.domain.statemachine;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-051")
class StatusChangeActorTest {

    @Test
    void aPersonAloneIsAccepted() {
        StatusChangeActor actor = StatusChangeActor.ofPerson(7L);

        assertThat(actor.personId()).isEqualTo(7L);
        assertThat(actor.processName()).isNull();
    }

    @Test
    void aProcessAloneIsAccepted() {
        StatusChangeActor actor = StatusChangeActor.ofProcess("MONTHLY_JOB");

        assertThat(actor.processName()).isEqualTo("MONTHLY_JOB");
        assertThat(actor.personId()).isNull();
    }

    @Test
    void bothOrNeitherAreRejected() {
        assertThatThrownBy(() -> new StatusChangeActor(1L, "JOB")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StatusChangeActor(null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aProcessNameOf80CharactersIsAcceptedAndOf81IsRejected() {
        assertThat(StatusChangeActor.ofProcess("a".repeat(80)).processName()).hasSize(80);
        assertThatThrownBy(() -> StatusChangeActor.ofProcess("a".repeat(81))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aBlankProcessNameIsRejected() {
        assertThatThrownBy(() -> StatusChangeActor.ofProcess("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StatusChangeActor.ofProcess("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
