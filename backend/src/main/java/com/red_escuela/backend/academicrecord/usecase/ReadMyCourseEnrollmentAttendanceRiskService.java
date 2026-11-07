package com.red_escuela.backend.academicrecord.usecase;

import com.red_escuela.backend.academicrecord.dto.StudentAttendanceRiskResponse;
import com.red_escuela.backend.academicrecord.specialist.AcademicRecordAttendanceFinder;
import com.red_escuela.backend.academicrecord.specialist.AcademicRecordEnrollmentOwnershipChecker;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ReadMyCourseEnrollmentAttendanceRiskService implements ReadMyCourseEnrollmentAttendanceRiskUseCase {

    private final AcademicRecordEnrollmentOwnershipChecker enrollmentOwnership;
    private final AcademicRecordAttendanceFinder attendanceFinder;

    public ReadMyCourseEnrollmentAttendanceRiskService(
            AcademicRecordEnrollmentOwnershipChecker enrollmentOwnership, AcademicRecordAttendanceFinder attendanceFinder) {
        this.enrollmentOwnership = enrollmentOwnership;
        this.attendanceFinder = attendanceFinder;
    }

    @Override
    public StudentAttendanceRiskResponse read(Long studentId, Long enrollmentId) {
        enrollmentOwnership.requireCourseEnrollmentOf(enrollmentId, studentId);
        return attendanceFinder.readCourseEnrollmentRisk(enrollmentId);
    }
}
