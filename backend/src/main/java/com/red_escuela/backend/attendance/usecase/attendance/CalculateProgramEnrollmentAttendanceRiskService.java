package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.dto.AttendanceRiskResponse;
import com.red_escuela.backend.attendance.service.AttendanceRiskCalculator;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CalculateProgramEnrollmentAttendanceRiskService implements CalculateProgramEnrollmentAttendanceRiskUseCase {

    private final AttendanceRiskCalculator riskCalculator;

    public CalculateProgramEnrollmentAttendanceRiskService(AttendanceRiskCalculator riskCalculator) {
        this.riskCalculator = riskCalculator;
    }

    @Override
    public AttendanceRiskResponse calculate(Long enrollmentId) {
        return riskCalculator.calculateForProgramEnrollment(enrollmentId);
    }
}
