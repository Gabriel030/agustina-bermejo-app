package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.attendance.api.AttendanceStandingFinder;
import com.red_escuela.backend.attendance.api.AttendanceStandingView;
import com.red_escuela.backend.enrollment.api.ActiveCourseSectionView;
import com.red_escuela.backend.enrollment.api.ActiveSectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.api.CourseEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityReason;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityVerdict;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CourseInstitutionalRegularityRuleTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    private final CourseEnrollmentFinder courseEnrollmentFinder = mock(CourseEnrollmentFinder.class);
    private final ActiveSectionEnrollmentFinder activeSectionEnrollmentFinder = mock(ActiveSectionEnrollmentFinder.class);
    private final AttendanceStandingFinder attendanceStandingFinder = mock(AttendanceStandingFinder.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC);
    private final CourseInstitutionalRegularityRule rule = new CourseInstitutionalRegularityRule(
            courseEnrollmentFinder, activeSectionEnrollmentFinder, attendanceStandingFinder, clock);

    private void givenEnrollmentWithStatus(EnrollmentStatus status) {
        CourseEnrollment enrollment = mock(CourseEnrollment.class);
        when(enrollment.getStatus()).thenReturn(status);
        when(courseEnrollmentFinder.requireById(5L)).thenReturn(enrollment);
    }

    private void givenSections(ActiveCourseSectionView... sections) {
        when(activeSectionEnrollmentFinder.activeCourseSections(5L)).thenReturn(List.of(sections));
    }

    private void givenStanding(Long sectionEnrollmentId, boolean free) {
        when(attendanceStandingFinder.ofCourseSectionEnrollment(sectionEnrollmentId))
                .thenReturn(new AttendanceStandingView(true, free));
    }

    @Test
    void courseFinishedYesterdayIsNotRegular() {
        givenEnrollmentWithStatus(EnrollmentStatus.ENROLLED);
        givenSections(new ActiveCourseSectionView(1L, TODAY.minusDays(1)));

        RegularityVerdict verdict = rule.evaluate(5L);

        assertThat(verdict.institutionallyRegular()).isFalse();
        assertThat(verdict.reasons()).containsExactly(RegularityReason.COURSE_FINISHED);
        verifyNoInteractions(attendanceStandingFinder);
    }

    @Test
    void courseEndingTodayIsStillRegular() {
        givenEnrollmentWithStatus(EnrollmentStatus.IN_PROGRESS);
        givenSections(new ActiveCourseSectionView(1L, TODAY));
        givenStanding(1L, false);

        assertThat(rule.evaluate(5L).institutionallyRegular()).isTrue();
    }

    @Test
    void waitlistedOrWithdrawnCourseIsNotRegular() {
        for (EnrollmentStatus status : List.of(EnrollmentStatus.WAITLISTED, EnrollmentStatus.WITHDRAWN,
                EnrollmentStatus.APPROVED, EnrollmentStatus.FAILED)) {
            givenEnrollmentWithStatus(status);

            RegularityVerdict verdict = rule.evaluate(5L);

            assertThat(verdict.institutionallyRegular()).as(status.name()).isFalse();
            assertThat(verdict.reasons()).as(status.name()).containsExactly(RegularityReason.NOT_ENROLLED);
        }
        verifyNoInteractions(activeSectionEnrollmentFinder, attendanceStandingFinder);
    }

    @Test
    void courseWithAllSectionsFreeIsNotRegular() {
        givenEnrollmentWithStatus(EnrollmentStatus.ENROLLED);
        givenSections(new ActiveCourseSectionView(1L, TODAY.plusDays(10)), new ActiveCourseSectionView(2L, TODAY.plusDays(10)));
        givenStanding(1L, true);
        givenStanding(2L, true);

        RegularityVerdict verdict = rule.evaluate(5L);

        assertThat(verdict.institutionallyRegular()).isFalse();
        assertThat(verdict.reasons()).containsExactly(RegularityReason.FREE_IN_ACTIVE_SECTION);
    }

    @Test
    void courseWithOneFreeSectionIsNotRegular() {
        givenEnrollmentWithStatus(EnrollmentStatus.ENROLLED);
        givenSections(new ActiveCourseSectionView(1L, TODAY.plusDays(10)), new ActiveCourseSectionView(2L, TODAY.plusDays(10)));
        givenStanding(1L, false);
        givenStanding(2L, true);

        assertThat(rule.evaluate(5L).institutionallyRegular()).isFalse();
    }

    @Test
    void finishedSectionWithFreeStandingDoesNotBlockAnOpenSection() {
        givenEnrollmentWithStatus(EnrollmentStatus.ENROLLED);
        givenSections(new ActiveCourseSectionView(1L, TODAY.minusDays(1)), new ActiveCourseSectionView(2L, TODAY));
        givenStanding(1L, true);
        givenStanding(2L, false);

        assertThat(rule.evaluate(5L).institutionallyRegular()).isTrue();
    }

    @Test
    void courseWithoutActiveSectionsIsNotRegular() {
        givenEnrollmentWithStatus(EnrollmentStatus.ENROLLED);
        givenSections();

        assertThat(rule.evaluate(5L).reasons()).containsExactly(RegularityReason.NO_ACTIVE_SECTION);
    }
}
