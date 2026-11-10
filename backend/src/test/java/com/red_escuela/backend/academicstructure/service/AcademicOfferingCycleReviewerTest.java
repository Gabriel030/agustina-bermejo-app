package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AcademicOfferingCycleReviewerTest {

    private final Person reviewer = new Person();
    private AcademicOfferingCycleReviewer service;
    private AcademicOfferingCycle cycle;

    @BeforeEach
    void setUp() {
        var finder = mock(CurrentActorFinder.class);
        when(finder.currentPerson()).thenReturn(reviewer);
        var clock = Clock.fixed(Instant.parse("2026-03-02T09:30:00Z"), ZoneOffset.UTC);
        service = new AcademicOfferingCycleReviewer(finder, clock);
        cycle = new AcademicOfferingCycle();
        cycle.setStatus(AcademicOfferingCycleStatus.PENDING_APPROVAL);
    }

    @Test
    void approvalIsDecidedByTheCurrentPersonAtTheClockInstant() {
        service.approve(cycle, "key-1");

        assertThat(cycle.getStatus()).isEqualTo(AcademicOfferingCycleStatus.APPROVED);
        assertThat(cycle.getDecidedBy()).isSameAs(reviewer);
        assertThat(cycle.getDecidedAt()).isEqualTo(LocalDateTime.of(2026, 3, 2, 9, 30));
        assertThat(cycle.getDecisionIdempotencyKey()).isEqualTo("key-1");
    }

    @Test
    void rejectionRecordsTheReasonTheCurrentPersonAndTheClockInstant() {
        service.reject(cycle, "missing teachers", "key-2");

        assertThat(cycle.getStatus()).isEqualTo(AcademicOfferingCycleStatus.REJECTED);
        assertThat(cycle.getRejectionReason()).isEqualTo("missing teachers");
        assertThat(cycle.getDecidedBy()).isSameAs(reviewer);
        assertThat(cycle.getDecidedAt()).isEqualTo(LocalDateTime.of(2026, 3, 2, 9, 30));
    }

    @Test
    void rejectionRemembersTheIdempotencyKeyOfTheDecision() {
        service.reject(cycle, "missing teachers", "key-2");

        assertThat(cycle.wasDecidedWithKey("key-2")).isTrue();
        assertThat(cycle.wasDecidedWithKey("other")).isFalse();
        assertThat(cycle.wasDecidedWithKey(null)).isFalse();
    }
}
