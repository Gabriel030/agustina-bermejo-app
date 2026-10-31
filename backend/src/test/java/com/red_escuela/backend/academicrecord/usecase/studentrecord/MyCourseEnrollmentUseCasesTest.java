package com.red_escuela.backend.academicrecord.usecase.studentrecord;

import com.red_escuela.backend.academicrecord.dto.StudentAttendanceResponse;
import com.red_escuela.backend.academicrecord.dto.StudentAttendanceRiskResponse;
import com.red_escuela.backend.academicrecord.dto.StudentGradeItemResponse;
import com.red_escuela.backend.academicrecord.service.AcademicRecordAttendanceFinder;
import com.red_escuela.backend.academicrecord.service.AcademicRecordEnrollmentOwnership;
import com.red_escuela.backend.academicrecord.service.AcademicRecordGradeItemFinder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MyCourseEnrollmentUseCasesTest {

    private final AcademicRecordEnrollmentOwnership ownership = mock(AcademicRecordEnrollmentOwnership.class);
    private final AcademicRecordAttendanceFinder attendanceFinder = mock(AcademicRecordAttendanceFinder.class);
    private final AcademicRecordGradeItemFinder gradeItemFinder = mock(AcademicRecordGradeItemFinder.class);
    private final ListMyCourseEnrollmentAttendanceService attendanceService =
            new ListMyCourseEnrollmentAttendanceService(ownership, attendanceFinder);
    private final ReadMyCourseEnrollmentAttendanceRiskService riskService =
            new ReadMyCourseEnrollmentAttendanceRiskService(ownership, attendanceFinder);
    private final ListMyCourseEnrollmentGradeItemsService gradeItemsService =
            new ListMyCourseEnrollmentGradeItemsService(ownership, gradeItemFinder);

    @Test
    void listsAttendanceOnlyAfterCheckingCourseOwnership() {
        List<StudentAttendanceResponse> attendance = List.of(
                new StudentAttendanceResponse(1L, 4L, null, "PRESENT", null, null, null));
        when(attendanceFinder.listCourseEnrollmentAttendance(4L)).thenReturn(attendance);

        assertThat(attendanceService.list(7L, 4L)).isEqualTo(attendance);

        var order = inOrder(ownership, attendanceFinder);
        order.verify(ownership).requireCourseEnrollmentOf(4L, 7L);
        order.verify(attendanceFinder).listCourseEnrollmentAttendance(4L);
    }

    @Test
    void readsRiskOnlyAfterCheckingCourseOwnership() {
        StudentAttendanceRiskResponse risk = new StudentAttendanceRiskResponse(true, false, null, null);
        when(attendanceFinder.readCourseEnrollmentRisk(4L)).thenReturn(risk);

        assertThat(riskService.read(7L, 4L)).isSameAs(risk);

        var order = inOrder(ownership, attendanceFinder);
        order.verify(ownership).requireCourseEnrollmentOf(4L, 7L);
        order.verify(attendanceFinder).readCourseEnrollmentRisk(4L);
    }

    @Test
    void listsGradeItemsOnlyAfterCheckingCourseOwnership() {
        List<StudentGradeItemResponse> items = List.of(new StudentGradeItemResponse(1L, 4L, "EXAM", 1, null, null, null));
        when(gradeItemFinder.listCourseEnrollmentGradeItems(4L)).thenReturn(items);

        assertThat(gradeItemsService.list(7L, 4L)).isEqualTo(items);

        var order = inOrder(ownership, gradeItemFinder);
        order.verify(ownership).requireCourseEnrollmentOf(4L, 7L);
        order.verify(gradeItemFinder).listCourseEnrollmentGradeItems(4L);
    }

    @Test
    void exposesNothingWhenTheStudentDoesNotOwnTheCourseEnrollment() {
        doThrow(new IllegalStateException("not owner")).when(ownership).requireCourseEnrollmentOf(4L, 7L);

        assertThatThrownBy(() -> attendanceService.list(7L, 4L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> riskService.read(7L, 4L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> gradeItemsService.list(7L, 4L)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(attendanceFinder, gradeItemFinder);
    }
}
