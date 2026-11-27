package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.attendance.dto.AttendanceEntry;
import com.red_escuela.backend.attendance.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.exception.ClassDateInFutureException;
import com.red_escuela.backend.attendance.repository.ClassSessionRepository;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SectionAttendanceLoaderClassDateTest {

    private static final Long SECTION_ID = 7L;
    private static final Long INSTRUCTOR_ID = 3L;
    private static final LocalDate CLASS_DATE = LocalDate.of(2026, 3, 11);

    private final SectionAttendanceRecorder attendanceRecorder = mock(SectionAttendanceRecorder.class);
    private final AttendanceSectionAccessChecker sectionAccess = mock(AttendanceSectionAccessChecker.class);
    private final ClassSessionUpserter classSessionUpserter = mock(ClassSessionUpserter.class);
    private final SectionEnrollmentFinder enrollmentFinder = mock(SectionEnrollmentFinder.class);
    private final ClassSessionRepository classSessionRepository = mock(ClassSessionRepository.class);
    private final ClassDateValidator classDateValidator = mock(ClassDateValidator.class);
    private final Instructor instructor = mock(Instructor.class);

    private final SectionAttendanceLoader loader = new SectionAttendanceLoader(
            attendanceRecorder, sectionAccess, classSessionUpserter, enrollmentFinder, classSessionRepository, classDateValidator);

    private CargarAsistenciaRequest request() {
        return new CargarAsistenciaRequest(CLASS_DATE, List.of(new AttendanceEntry(10L, AttendanceStatus.PRESENT)), null, null);
    }

    @Test
    void programLoadWithAnInvalidClassDateRecordsNothing() {
        when(sectionAccess.findProgramSection(SECTION_ID)).thenReturn(mock(SubjectSection.class));
        when(sectionAccess.resolveInstructor(INSTRUCTOR_ID)).thenReturn(instructor);
        doThrow(new ClassDateInFutureException(CLASS_DATE)).when(classDateValidator).requireValidProgramClassDate(SECTION_ID, CLASS_DATE);

        assertThatThrownBy(() -> loader.loadProgramSectionAttendance(SECTION_ID, INSTRUCTOR_ID, request()))
                .isInstanceOf(ClassDateInFutureException.class);

        verifyNoInteractions(attendanceRecorder, classSessionUpserter);
    }

    @Test
    void courseLoadWithAnInvalidClassDateRecordsNothing() {
        when(sectionAccess.findCourseSection(SECTION_ID)).thenReturn(mock(CourseSubjectSection.class));
        when(sectionAccess.resolveInstructor(INSTRUCTOR_ID)).thenReturn(instructor);
        doThrow(new ClassDateInFutureException(CLASS_DATE)).when(classDateValidator).requireValidCourseClassDate(SECTION_ID, CLASS_DATE);

        assertThatThrownBy(() -> loader.loadCourseSectionAttendance(SECTION_ID, INSTRUCTOR_ID, request()))
                .isInstanceOf(ClassDateInFutureException.class);

        verifyNoInteractions(attendanceRecorder, classSessionUpserter);
    }

    @Test
    void programAndCourseLoadsValidateTheDateAgainstTheirOwnSection() {
        when(sectionAccess.findProgramSection(SECTION_ID)).thenReturn(mock(SubjectSection.class));
        when(sectionAccess.findCourseSection(SECTION_ID)).thenReturn(mock(CourseSubjectSection.class));
        when(sectionAccess.resolveInstructor(INSTRUCTOR_ID)).thenReturn(instructor);

        loader.loadProgramSectionAttendance(SECTION_ID, INSTRUCTOR_ID, new CargarAsistenciaRequest(CLASS_DATE, List.of(), null, null));
        loader.loadCourseSectionAttendance(SECTION_ID, INSTRUCTOR_ID, new CargarAsistenciaRequest(CLASS_DATE, List.of(), null, null));

        verify(classDateValidator).requireValidProgramClassDate(SECTION_ID, CLASS_DATE);
        verify(classDateValidator).requireValidCourseClassDate(SECTION_ID, CLASS_DATE);
    }
}
