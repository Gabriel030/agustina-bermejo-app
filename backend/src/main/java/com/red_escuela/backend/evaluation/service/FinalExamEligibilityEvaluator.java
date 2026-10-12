package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.evaluation.exception.FinalExamRequiresRegularStudentException;
import com.red_escuela.backend.evaluation.policy.FinalExamLoadRequest;
import com.red_escuela.backend.evaluation.policy.RegularStudentForFinalExamPolicy;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FinalExamEligibilityEvaluator {
    private final PolicySet<FinalExamLoadRequest> finalExamLoadPolicies =
            PolicySet.named("FINAL_EXAM_LOAD", List.of(new RegularStudentForFinalExamPolicy()));

    public void requireEligible(Long enrollmentId, boolean regularInSubject) {
        finalExamLoadPolicies.evaluateRulesUntilFirstViolation(new FinalExamLoadRequest(enrollmentId, regularInSubject))
                .throwIfDenied(FinalExamRequiresRegularStudentException::new);
    }
}
