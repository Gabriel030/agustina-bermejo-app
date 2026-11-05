package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AcademicOfferingCycleDrafterTest {

    @Test
    void draftIsCreatedForTheTermByTheCurrentPersonAtTheClockInstant() {
        var finder = mock(CurrentActorFinder.class);
        var person = new Person();
        when(finder.currentPerson()).thenReturn(person);
        var clock = Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC);
        var term = new Term();

        var cycle = new AcademicOfferingCycleDrafter(finder, clock).draftFor(term);

        assertThat(cycle.getTerm()).isSameAs(term);
        assertThat(cycle.getCreatedBy()).isSameAs(person);
        assertThat(cycle.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 3, 1, 10, 0));
        assertThat(cycle.getStatus()).isEqualTo(AcademicOfferingCycleStatus.DRAFT);
    }
}
