package com.red_escuela.backend.graduation.certificate.approvedsubjects.port;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.policy.ApprovedSubjectsEligibilityInput;

import java.time.LocalDateTime;

public interface ApprovedSubjectsEligibilityFacts {

    ApprovedSubjectsEligibilityInput inputFor(Long studentId, OwnedEnrollment enrollment, LocalDateTime now);
}
