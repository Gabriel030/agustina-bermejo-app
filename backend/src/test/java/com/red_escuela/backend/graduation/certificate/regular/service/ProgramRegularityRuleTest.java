package com.red_escuela.backend.graduation.certificate.regular.service;

import com.red_escuela.backend.attendance.api.AttendanceStandingFinder;
import com.red_escuela.backend.attendance.api.AttendanceStandingView;
import com.red_escuela.backend.enrollment.api.ActiveSectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityReason;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityVerdict;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProgramRegularityRuleTest {

    private final ProgramEnrollmentFinder programEnrollmentFinder = mock(ProgramEnrollmentFinder.class);
    private final ActiveSectionEnrollmentFinder activeSectionEnrollmentFinder = mock(ActiveSectionEnrollmentFinder.class);
    private final AttendanceStandingFinder attendanceStandingFinder = mock(AttendanceStandingFinder.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC);
    private final ProgramRegularityRule rule = new ProgramRegularityRule(
            programEnrollmentFinder, activeSectionEnrollmentFinder, attendanceStandingFinder, clock);

    private void givenEnrollmentWithStatus(ProgramEnrollmentStatus status) {
        ProgramEnrollment enrollment = mock(ProgramEnrollment.class);
        when(enrollment.getStatus()).thenReturn(status);
        when(programEnrollmentFinder.requireById(17L)).thenReturn(enrollment);
    }

    private void givenStanding(Long sectionEnrollmentId, boolean calculable, boolean free) {
        when(attendanceStandingFinder.ofProgramSectionEnrollment(sectionEnrollmentId))
                .thenReturn(new AttendanceStandingView(calculable, free));
    }

    @Test
    void enrolledProgramWithNonFreeSectionIsRegular() {
        givenEnrollmentWithStatus(ProgramEnrollmentStatus.ENROLLED);
        when(activeSectionEnrollmentFinder.activeProgramSectionEnrollmentIds(17L)).thenReturn(List.of(1L));
        givenStanding(1L, true, false);

        RegularityVerdict verdict = rule.evaluate(17L);

        assertThat(verdict.regular()).isTrue();
        assertThat(verdict.reasons()).isEmpty();
        assertThat(verdict.attendanceControlled()).isTrue();
        assertThat(verdict.evaluatedAt()).isEqualTo(clock.instant());
    }

    @Test
    void programWithOneFreeAndOneNonFreeSectionIsNotRegular() {
        givenEnrollmentWithStatus(ProgramEnrollmentStatus.ENROLLED);
        when(activeSectionEnrollmentFinder.activeProgramSectionEnrollmentIds(17L)).thenReturn(List.of(1L, 2L));
        givenStanding(1L, true, false);
        givenStanding(2L, true, true);

        RegularityVerdict verdict = rule.evaluate(17L);

        assertThat(verdict.regular()).isFalse();
        assertThat(verdict.reasons()).containsExactly(RegularityReason.FREE_IN_ACTIVE_SECTION);
    }

    @Test
    void programWithAllSectionsFreeIsNotRegular() {
        givenEnrollmentWithStatus(ProgramEnrollmentStatus.ENROLLED);
        when(activeSectionEnrollmentFinder.activeProgramSectionEnrollmentIds(17L)).thenReturn(List.of(1L, 2L));
        givenStanding(1L, true, true);
        givenStanding(2L, true, true);

        RegularityVerdict verdict = rule.evaluate(17L);

        assertThat(verdict.regular()).isFalse();
        assertThat(verdict.reasons()).containsExactly(RegularityReason.FREE_IN_ACTIVE_SECTION);
        assertThat(verdict.attendanceControlled()).isTrue();
    }

    @Test
    void pausedWithdrawnOrCompletedProgramIsNotRegular() {
        for (ProgramEnrollmentStatus status : ProgramEnrollmentStatus.values()) {
            if (status == ProgramEnrollmentStatus.ENROLLED) {
                continue;
            }
            givenEnrollmentWithStatus(status);

            RegularityVerdict verdict = rule.evaluate(17L);

            assertThat(verdict.regular()).as(status.name()).isFalse();
            assertThat(verdict.reasons()).as(status.name()).containsExactly(RegularityReason.PROGRAM_NOT_ENROLLED);
        }
        verifyNoInteractions(activeSectionEnrollmentFinder, attendanceStandingFinder);
    }

    @Test
    void programWithoutActiveSectionsIsNotRegular() {
        givenEnrollmentWithStatus(ProgramEnrollmentStatus.ENROLLED);
        when(activeSectionEnrollmentFinder.activeProgramSectionEnrollmentIds(17L)).thenReturn(List.of());

        RegularityVerdict verdict = rule.evaluate(17L);

        assertThat(verdict.regular()).isFalse();
        assertThat(verdict.reasons()).containsExactly(RegularityReason.NO_ACTIVE_SECTION);
        verifyNoInteractions(attendanceStandingFinder);
    }

    @Test
    void sectionWithoutAbsenceLimitIsNotControlledAndDoesNotBlock() {
        givenEnrollmentWithStatus(ProgramEnrollmentStatus.ENROLLED);
        when(activeSectionEnrollmentFinder.activeProgramSectionEnrollmentIds(17L)).thenReturn(List.of(1L));
        givenStanding(1L, false, false);

        RegularityVerdict verdict = rule.evaluate(17L);

        assertThat(verdict.regular()).isTrue();
        assertThat(verdict.attendanceControlled()).isFalse();
    }

    @Test
    void controlledSectionMakesAttendanceControlledEvenIfAnotherIsNot() {
        givenEnrollmentWithStatus(ProgramEnrollmentStatus.ENROLLED);
        when(activeSectionEnrollmentFinder.activeProgramSectionEnrollmentIds(17L)).thenReturn(List.of(1L, 2L));
        givenStanding(1L, false, false);
        givenStanding(2L, true, false);

        assertThat(rule.evaluate(17L).attendanceControlled()).isTrue();
    }
}
