package com.red_escuela.backend.graduation.service;

import com.red_escuela.backend.enrollment.api.ProgramProgressReader;
import org.springframework.stereotype.Service;

@Service
public class GraduationEligibilityChecker {

    private final ProgramProgressReader programProgressReader;

    public GraduationEligibilityChecker(ProgramProgressReader programProgressReader) {
        this.programProgressReader = programProgressReader;
    }

    public boolean isEligibleToGraduate(Long programEnrollmentId) {
        return programProgressReader.calculateProgress(programEnrollmentId).eligibleToComplete();
    }
}
