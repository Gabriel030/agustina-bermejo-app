package com.red_escuela.backend.academicrecord.usecase.studentrecord;

import com.red_escuela.backend.academicrecord.dto.StudentAttendanceRiskResponse;
import com.red_escuela.backend.academicrecord.service.AcademicRecordAttendanceFinder;
import com.red_escuela.backend.academicrecord.service.AcademicRecordEnrollmentOwnership;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ReadMyCourseEnrollmentAttendanceRiskService implements ReadMyCourseEnrollmentAttendanceRiskUseCase {

    private final AcademicRecordEnrollmentOwnership enrollmentOwnership;
    private final AcademicRecordAttendanceFinder attendanceFinder;

    public ReadMyCourseEnrollmentAttendanceRiskService(
            AcademicRecordEnrollmentOwnership enrollmentOwnership, AcademicRecordAttendanceFinder attendanceFinder) {
        this.enrollmentOwnership = enrollmentOwnership;
        this.attendanceFinder = attendanceFinder;
    }

    @Override
    public StudentAttendanceRiskResponse read(Long studentId, Long enrollmentId) {
        enrollmentOwnership.requireCourseEnrollmentOf(enrollmentId, studentId);
        return attendanceFinder.readCourseEnrollmentRisk(enrollmentId);
    }
}
