package com.red_escuela.backend.academicrecord.usecase;

import com.red_escuela.backend.academicrecord.dto.StudentAttendanceResponse;
import com.red_escuela.backend.academicrecord.specialist.AcademicRecordAttendanceFinder;
import com.red_escuela.backend.academicrecord.specialist.AcademicRecordEnrollmentOwnershipChecker;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListMyEnrollmentAttendanceService implements ListMyEnrollmentAttendanceUseCase {

    private final AcademicRecordEnrollmentOwnershipChecker enrollmentOwnership;
    private final AcademicRecordAttendanceFinder attendanceFinder;

    public ListMyEnrollmentAttendanceService(
            AcademicRecordEnrollmentOwnershipChecker enrollmentOwnership, AcademicRecordAttendanceFinder attendanceFinder) {
        this.enrollmentOwnership = enrollmentOwnership;
        this.attendanceFinder = attendanceFinder;
    }

    @Override
    public List<StudentAttendanceResponse> list(Long studentId, Long enrollmentId) {
        enrollmentOwnership.requireProgramEnrollmentOf(enrollmentId, studentId);
        return attendanceFinder.listProgramEnrollmentAttendance(enrollmentId);
    }
}
