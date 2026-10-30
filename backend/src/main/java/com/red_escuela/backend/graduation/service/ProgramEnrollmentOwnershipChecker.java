package com.red_escuela.backend.graduation.service;

import com.red_escuela.backend.enrollment.api.ProgramEnrollmentFinder;
import org.springframework.stereotype.Service;

@Service
public class ProgramEnrollmentOwnershipChecker {

    private final ProgramEnrollmentFinder programEnrollmentFinder;

    public ProgramEnrollmentOwnershipChecker(ProgramEnrollmentFinder programEnrollmentFinder) {
        this.programEnrollmentFinder = programEnrollmentFinder;
    }

    public void requireOwnedBy(Long programEnrollmentId, Long studentId) {
        programEnrollmentFinder.requireOwnedBy(programEnrollmentId, studentId);
    }
}
