package com.red_escuela.backend.academicrecord.usecase;

import com.red_escuela.backend.academicrecord.dto.StudentAttendanceResponse;
import com.red_escuela.backend.academicrecord.specialist.AcademicRecordAttendanceFinder;
import com.red_escuela.backend.academicrecord.specialist.AcademicRecordEnrollmentOwnershipChecker;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListMyCourseEnrollmentAttendanceService implements ListMyCourseEnrollmentAttendanceUseCase {

    private final AcademicRecordEnrollmentOwnershipChecker enrollmentOwnership;
    private final AcademicRecordAttendanceFinder attendanceFinder;

    public ListMyCourseEnrollmentAttendanceService(
            AcademicRecordEnrollmentOwnershipChecker enrollmentOwnership, AcademicRecordAttendanceFinder attendanceFinder) {
        this.enrollmentOwnership = enrollmentOwnership;
        this.attendanceFinder = attendanceFinder;
    }

    @Override
    public List<StudentAttendanceResponse> list(Long studentId, Long enrollmentId) {
        enrollmentOwnership.requireCourseEnrollmentOf(enrollmentId, studentId);
        return attendanceFinder.listCourseEnrollmentAttendance(enrollmentId);
    }
}
