package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.enrollment.api.FinalExamEligibilityResolver;
import com.red_escuela.backend.enrollment.api.FinalExamEligibilityResponse;
import com.red_escuela.backend.enrollment.api.FinalExamIneligibilityReason;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.exception.FinalExamBlockedByAbsencesException;
import com.red_escuela.backend.evaluation.exception.FinalExamRequiresRegularStudentException;
import com.red_escuela.backend.evaluation.exception.StudentFreeByAbsencesException;
import com.red_escuela.backend.evaluation.exception.StudentNotRegularException;
import com.red_escuela.backend.evaluation.policy.FinalExamEligibilityRequest;
import com.red_escuela.backend.evaluation.policy.NotFreeByAbsencesForFinalExamPolicy;
import com.red_escuela.backend.evaluation.policy.RegularStudentForFinalExamPolicy;
import com.red_escuela.backend.shared.domain.policy.PolicyEvaluationResult;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FinalExamEligibilityEvaluator {
    private final PolicySet<FinalExamEligibilityRequest> finalExamPolicies = PolicySet.named("FINAL_EXAM_ELIGIBILITY",
            List.of(new RegularStudentForFinalExamPolicy(), new NotFreeByAbsencesForFinalExamPolicy()));

    private final FinalExamEligibilityResolver eligibilityResolver;

    public FinalExamEligibilityEvaluator(FinalExamEligibilityResolver eligibilityResolver) {
        this.eligibilityResolver = eligibilityResolver;
    }

    public void requireEligibleToLoad(SubjectSectionEnrollment enrollment) {
        requireEligibleToLoad(enrollment.getId(), eligibilityResolver.resolve(enrollment));
    }

    public void requireEligibleToLoad(CourseSubjectSectionEnrollment enrollment) {
        requireEligibleToLoad(enrollment.getId(), eligibilityResolver.resolve(enrollment));
    }

    public void requireEligibleToRegister(SubjectSectionEnrollment enrollment) {
        requireEligibleToRegister(enrollment.getId(), eligibilityResolver.resolve(enrollment));
    }

    public void requireEligibleToRegister(CourseSubjectSectionEnrollment enrollment) {
        requireEligibleToRegister(enrollment.getId(), eligibilityResolver.resolve(enrollment));
    }

    private void requireEligibleToLoad(Long enrollmentId, FinalExamEligibilityResponse eligibility) {
        evaluate(enrollmentId, eligibility).throwIfDenied(violations ->
                eligibility.reason() == FinalExamIneligibilityReason.COURSEWORK_NOT_REGULAR
                        ? new FinalExamRequiresRegularStudentException(violations)
                        : new FinalExamBlockedByAbsencesException(violations));
    }

    private void requireEligibleToRegister(Long enrollmentId, FinalExamEligibilityResponse eligibility) {
        evaluate(enrollmentId, eligibility).throwIfDenied(violations ->
                eligibility.reason() == FinalExamIneligibilityReason.COURSEWORK_NOT_REGULAR
                        ? new StudentNotRegularException(enrollmentId)
                        : new StudentFreeByAbsencesException(enrollmentId));
    }

    private PolicyEvaluationResult evaluate(Long enrollmentId, FinalExamEligibilityResponse eligibility) {
        return finalExamPolicies.evaluateRulesUntilFirstViolation(new FinalExamEligibilityRequest(enrollmentId, eligibility));
    }
}
