package com.red_escuela.backend.academicrecord.usecase.studentrecord;

import com.red_escuela.backend.academicrecord.dto.StudentAttendanceResponse;
import com.red_escuela.backend.academicrecord.service.AcademicRecordAttendanceFinder;
import com.red_escuela.backend.academicrecord.service.AcademicRecordEnrollmentOwnership;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListMyEnrollmentAttendanceService implements ListMyEnrollmentAttendanceUseCase {

    private final AcademicRecordEnrollmentOwnership enrollmentOwnership;
    private final AcademicRecordAttendanceFinder attendanceFinder;

    public ListMyEnrollmentAttendanceService(
            AcademicRecordEnrollmentOwnership enrollmentOwnership, AcademicRecordAttendanceFinder attendanceFinder) {
        this.enrollmentOwnership = enrollmentOwnership;
        this.attendanceFinder = attendanceFinder;
    }

    @Override
    public List<StudentAttendanceResponse> list(Long studentId, Long enrollmentId) {
        enrollmentOwnership.requireProgramEnrollmentOf(enrollmentId, studentId);
        return attendanceFinder.listProgramEnrollmentAttendance(enrollmentId);
    }
}
