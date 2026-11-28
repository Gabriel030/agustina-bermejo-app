package com.red_escuela.backend.academicrecord.usecase;

import com.red_escuela.backend.academicrecord.dto.MyCourseEnrollmentDetailResponse;
import com.red_escuela.backend.academicrecord.dto.StudentAttendanceResponse;
import com.red_escuela.backend.academicrecord.dto.StudentAttendanceRiskResponse;
import com.red_escuela.backend.academicrecord.dto.StudentCourseSectionEnrollmentResponse;
import com.red_escuela.backend.academicrecord.dto.StudentGradeItemResponse;
import com.red_escuela.backend.academicrecord.specialist.AcademicRecordAttendanceFinder;
import com.red_escuela.backend.academicrecord.specialist.AcademicRecordCourseEnrollmentFinder;
import com.red_escuela.backend.academicrecord.specialist.AcademicRecordCourseSectionEnrollmentFinder;
import com.red_escuela.backend.academicrecord.specialist.AcademicRecordGradeItemFinder;
import com.red_escuela.backend.enrollment.api.CourseEnrollmentDetailView;
import com.red_escuela.backend.enrollment.api.CourseEnrollmentNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReadMyCourseEnrollmentDetailServiceTest {

    private final AcademicRecordCourseEnrollmentFinder courseEnrollmentFinder = mock(AcademicRecordCourseEnrollmentFinder.class);
    private final AcademicRecordCourseSectionEnrollmentFinder sectionFinder = mock(AcademicRecordCourseSectionEnrollmentFinder.class);
    private final AcademicRecordAttendanceFinder attendanceFinder = mock(AcademicRecordAttendanceFinder.class);
    private final AcademicRecordGradeItemFinder gradeItemFinder = mock(AcademicRecordGradeItemFinder.class);
    private final ReadMyCourseEnrollmentDetailService service = new ReadMyCourseEnrollmentDetailService(
            courseEnrollmentFinder, sectionFinder, attendanceFinder, gradeItemFinder);

    private StudentCourseSectionEnrollmentResponse sectionEnrollment(Long id, String subjectName) {
        return new StudentCourseSectionEnrollmentResponse(
                id, 4L, 3L, "Curso A", 50L + id, "Comisión " + id, 60L + id, subjectName, "IN_PROGRESS", 1, null, null,
                null, null, "Lunes 18:00", "Titular " + id, null);
    }

    @Test
    void ownedEnrollmentIsBuiltWithEachSubjectOwnAttendanceRiskAndGrades() {
        CourseEnrollmentDetailView view = new CourseEnrollmentDetailView(4L, 3L, "Curso A", "IN_PROGRESS", null, null);
        StudentCourseSectionEnrollmentResponse first = sectionEnrollment(10L, "Módulo 1");
        StudentCourseSectionEnrollmentResponse second = sectionEnrollment(11L, "Módulo 2");
        StudentAttendanceRiskResponse firstRisk = new StudentAttendanceRiskResponse(true, false, null, null);
        StudentAttendanceRiskResponse secondRisk = new StudentAttendanceRiskResponse(true, true, null, null);
        List<StudentAttendanceResponse> firstAttendance = List.of(new StudentAttendanceResponse(1L, 10L, null, "PRESENT", null, null, null, null));
        List<StudentAttendanceResponse> secondAttendance = List.of(new StudentAttendanceResponse(2L, 11L, null, "ABSENT", null, null, null, null));
        List<StudentGradeItemResponse> firstGrades = List.of(new StudentGradeItemResponse(1L, 10L, "EXAM", 1, null, null, null, null));
        when(courseEnrollmentFinder.readOfStudent(4L, 7L)).thenReturn(view);
        when(sectionFinder.listOfStudent(7L, 4L)).thenReturn(List.of(first, second));
        when(attendanceFinder.readCourseEnrollmentRisk(10L)).thenReturn(firstRisk);
        when(attendanceFinder.readCourseEnrollmentRisk(11L)).thenReturn(secondRisk);
        when(attendanceFinder.listCourseEnrollmentAttendance(10L)).thenReturn(firstAttendance);
        when(attendanceFinder.listCourseEnrollmentAttendance(11L)).thenReturn(secondAttendance);
        when(gradeItemFinder.listCourseEnrollmentGradeItems(10L)).thenReturn(firstGrades);
        when(gradeItemFinder.listCourseEnrollmentGradeItems(11L)).thenReturn(List.of());

        MyCourseEnrollmentDetailResponse detail = service.read(7L, 4L);

        assertThat(detail.id()).isEqualTo(4L);
        assertThat(detail.courseName()).isEqualTo("Curso A");
        assertThat(detail.subjects()).containsExactly(
                new MyCourseEnrollmentDetailResponse.Subject(first, firstRisk, firstAttendance, firstGrades),
                new MyCourseEnrollmentDetailResponse.Subject(second, secondRisk, secondAttendance, List.of()));
    }

    @Test
    void enrollmentWithoutSectionsReturnsNoSubjects() {
        when(courseEnrollmentFinder.readOfStudent(4L, 7L))
                .thenReturn(new CourseEnrollmentDetailView(4L, 3L, "Curso A", "ENROLLED", null, null));
        when(sectionFinder.listOfStudent(7L, 4L)).thenReturn(List.of());

        assertThat(service.read(7L, 4L).subjects()).isEmpty();
    }

    @Test
    void enrollmentOfAnotherStudentReadsNothingElse() {
        when(courseEnrollmentFinder.readOfStudent(4L, 7L)).thenThrow(new CourseEnrollmentNotFoundException(4L));

        assertThatThrownBy(() -> service.read(7L, 4L)).isInstanceOf(CourseEnrollmentNotFoundException.class);
        verifyNoInteractions(sectionFinder, attendanceFinder, gradeItemFinder);
    }
}
