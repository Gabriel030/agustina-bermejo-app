package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.SectionScheduleFinder;
import com.red_escuela.backend.academicstructure.api.SectionSlotView;
import com.red_escuela.backend.academicstructure.api.TermPeriodFinder;
import com.red_escuela.backend.academicstructure.api.TermPeriodView;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.SectionScheduleOverlapException;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudentScheduleOverlapGuardTest {

    private static final long STUDENT = 1L;
    private static final long TARGET_SECTION = 100L;
    private static final long TARGET_TERM = 10L;

    private final SubjectSectionEnrollmentRepository subjectRepository = mock(SubjectSectionEnrollmentRepository.class);
    private final CourseSubjectSectionEnrollmentRepository courseRepository = mock(CourseSubjectSectionEnrollmentRepository.class);
    private final SectionScheduleFinder scheduleFinder = mock(SectionScheduleFinder.class);
    private final TermPeriodFinder periodFinder = mock(TermPeriodFinder.class);
    private final StudentScheduleOverlapGuard guard = new StudentScheduleOverlapGuard(subjectRepository, courseRepository, scheduleFinder, periodFinder);

    private static SectionSlotView slot(DayOfWeek day, int fromHour, int toHour) {
        return new SectionSlotView(day, LocalTime.of(fromHour, 0), LocalTime.of(toHour, 0), "Aula");
    }

    private void period(long termId, int fromMonth, int toMonth) {
        when(periodFinder.findPeriod(termId)).thenReturn(new TermPeriodView(termId, LocalDateTime.of(2026, fromMonth, 1, 0, 0), LocalDateTime.of(2026, toMonth, 28, 0, 0)));
    }

    private SubjectSectionEnrollment programEnrollment(long id, long sectionId, long termId, String subjectName, EnrollmentStatus status) {
        Subject subject = new Subject();
        subject.setName(subjectName);
        Term term = new Term();
        term.setId(termId);
        SubjectSection section = new SubjectSection();
        section.setId(sectionId);
        section.setSubject(subject);
        section.setTerm(term);
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setId(id);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(status);
        return enrollment;
    }

    @Test
    void slotThatStartsExactlyWhenTheOtherEndsDoesNotOverlap() {
        period(TARGET_TERM, 3, 7);
        when(scheduleFinder.findProgramSectionSlots(TARGET_SECTION)).thenReturn(List.of(slot(DayOfWeek.MONDAY, 10, 12)));
        when(scheduleFinder.findProgramSectionSlots(200L)).thenReturn(List.of(slot(DayOfWeek.MONDAY, 8, 10)));
        when(subjectRepository.findByStudentId(STUDENT)).thenReturn(List.of(programEnrollment(2L, 200L, TARGET_TERM, "Algebra", EnrollmentStatus.ENROLLED)));

        assertThatCode(() -> guard.requireNoOverlapForProgramSection(STUDENT, TARGET_SECTION, TARGET_TERM, 1L)).doesNotThrowAnyException();
    }

    @Test
    void slotThatOverlapsInTheSamePeriodNamesTheConflictingSubjectDayAndTimes() {
        period(TARGET_TERM, 3, 7);
        when(scheduleFinder.findProgramSectionSlots(TARGET_SECTION)).thenReturn(List.of(slot(DayOfWeek.MONDAY, 9, 11)));
        when(scheduleFinder.findProgramSectionSlots(200L)).thenReturn(List.of(slot(DayOfWeek.MONDAY, 8, 10)));
        when(subjectRepository.findByStudentId(STUDENT)).thenReturn(List.of(programEnrollment(2L, 200L, TARGET_TERM, "Algebra", EnrollmentStatus.IN_PROGRESS)));

        assertThatThrownBy(() -> guard.requireNoOverlapForProgramSection(STUDENT, TARGET_SECTION, TARGET_TERM, 1L))
                .isInstanceOf(SectionScheduleOverlapException.class)
                .hasMessageContaining("Algebra").hasMessageContaining("lunes").hasMessageContaining("08:00").hasMessageContaining("10:00");
    }

    @Test
    void sameScheduleInADisjointPeriodDoesNotOverlap() {
        period(TARGET_TERM, 3, 7);
        period(11L, 8, 12);
        when(scheduleFinder.findProgramSectionSlots(TARGET_SECTION)).thenReturn(List.of(slot(DayOfWeek.MONDAY, 8, 10)));
        when(scheduleFinder.findProgramSectionSlots(200L)).thenReturn(List.of(slot(DayOfWeek.MONDAY, 8, 10)));
        when(subjectRepository.findByStudentId(STUDENT)).thenReturn(List.of(programEnrollment(2L, 200L, 11L, "Algebra", EnrollmentStatus.ENROLLED)));

        assertThatCode(() -> guard.requireNoOverlapForProgramSection(STUDENT, TARGET_SECTION, TARGET_TERM, 1L)).doesNotThrowAnyException();
    }

    @Test
    void sameHoursOnAnotherDayDoNotOverlap() {
        period(TARGET_TERM, 3, 7);
        when(scheduleFinder.findProgramSectionSlots(TARGET_SECTION)).thenReturn(List.of(slot(DayOfWeek.TUESDAY, 8, 10)));
        when(scheduleFinder.findProgramSectionSlots(200L)).thenReturn(List.of(slot(DayOfWeek.MONDAY, 8, 10)));
        when(subjectRepository.findByStudentId(STUDENT)).thenReturn(List.of(programEnrollment(2L, 200L, TARGET_TERM, "Algebra", EnrollmentStatus.ENROLLED)));

        assertThatCode(() -> guard.requireNoOverlapForProgramSection(STUDENT, TARGET_SECTION, TARGET_TERM, 1L)).doesNotThrowAnyException();
    }

    @Test
    void withdrawnWaitlistedAndTheIgnoredEnrollmentDoNotCount() {
        period(TARGET_TERM, 3, 7);
        when(scheduleFinder.findProgramSectionSlots(TARGET_SECTION)).thenReturn(List.of(slot(DayOfWeek.MONDAY, 8, 10)));
        when(scheduleFinder.findProgramSectionSlots(200L)).thenReturn(List.of(slot(DayOfWeek.MONDAY, 8, 10)));
        when(subjectRepository.findByStudentId(STUDENT)).thenReturn(List.of(
                programEnrollment(2L, 200L, TARGET_TERM, "Retirada", EnrollmentStatus.WITHDRAWN),
                programEnrollment(3L, 200L, TARGET_TERM, "Espera", EnrollmentStatus.WAITLISTED),
                programEnrollment(1L, 200L, TARGET_TERM, "Origen", EnrollmentStatus.ENROLLED)));

        assertThatCode(() -> guard.requireNoOverlapForProgramSection(STUDENT, TARGET_SECTION, TARGET_TERM, 1L)).doesNotThrowAnyException();
    }

    @Test
    void overlapWithAnActiveCourseModuleIsRejectedNamingTheModule() {
        period(TARGET_TERM, 3, 7);
        Term term = new Term();
        term.setId(TARGET_TERM);
        CourseSubjectSection courseSection = mock(CourseSubjectSection.class);
        when(courseSection.getId()).thenReturn(300L);
        when(courseSection.getTerm()).thenReturn(term);
        when(courseSection.getModuleName()).thenReturn("Excel avanzado");
        CourseSubjectSectionEnrollment courseEnrollment = new CourseSubjectSectionEnrollment();
        courseEnrollment.setCourseSubjectSection(courseSection);
        when(scheduleFinder.findProgramSectionSlots(TARGET_SECTION)).thenReturn(List.of(slot(DayOfWeek.WEDNESDAY, 18, 20)));
        when(scheduleFinder.findCourseSectionSlots(300L)).thenReturn(List.of(slot(DayOfWeek.WEDNESDAY, 19, 21)));
        when(subjectRepository.findByStudentId(STUDENT)).thenReturn(List.of());
        when(courseRepository.findByStudentIdAndStatusInOrderByIdAsc(org.mockito.ArgumentMatchers.eq(STUDENT), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(courseEnrollment));

        assertThatThrownBy(() -> guard.requireNoOverlapForProgramSection(STUDENT, TARGET_SECTION, TARGET_TERM, 1L))
                .isInstanceOf(SectionScheduleOverlapException.class).hasMessageContaining("Excel avanzado").hasMessageContaining("miércoles");
    }

    @Test
    void targetSectionWithoutScheduleNeverOverlaps() {
        when(scheduleFinder.findProgramSectionSlots(TARGET_SECTION)).thenReturn(List.of());

        assertThatCode(() -> guard.requireNoOverlapForProgramSection(STUDENT, TARGET_SECTION, TARGET_TERM, 1L)).doesNotThrowAnyException();
    }
}
