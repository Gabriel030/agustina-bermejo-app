package com.red_escuela.backend.evaluation.api;

public interface GraduationRequestTriggerPort {
    void requestGraduationIfEligible(Long programEnrollmentId);
}
