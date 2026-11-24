package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.enrollment.api.PrerequisiteAssessment;
import com.red_escuela.backend.enrollment.api.ProgramPrerequisiteEvaluator;
import com.red_escuela.backend.evaluation.exception.FinalExamPrerequisitesNotApprovedException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FinalExamPrerequisiteChecker {
    private final ProgramPrerequisiteEvaluator programPrerequisiteEvaluator;

    public FinalExamPrerequisiteChecker(ProgramPrerequisiteEvaluator programPrerequisiteEvaluator) {
        this.programPrerequisiteEvaluator = programPrerequisiteEvaluator;
    }

    public void requireAllApprovedOrWaived(Long programEnrollmentId, Long subjectId, Long enrollmentId) {
        List<Map<String, Object>> pending = programPrerequisiteEvaluator
                .findUnmetForFinalExamOrEmptyOutsidePlan(programEnrollmentId, subjectId).stream()
                .map(FinalExamPrerequisiteChecker::pendingPrerequisiteOf)
                .toList();
        if (!pending.isEmpty()) {
            throw new FinalExamPrerequisitesNotApprovedException(enrollmentId, pending);
        }
    }

    private static Map<String, Object> pendingPrerequisiteOf(PrerequisiteAssessment assessment) {
        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("subjectId", assessment.subjectId());
        pending.put("subjectName", assessment.subjectName());
        pending.put("standing", assessment.standingName());
        if (assessment.minGrade() != null) {
            pending.put("minGrade", assessment.minGrade().value());
        }
        if (assessment.approvedScore() != null) {
            pending.put("approvedScore", assessment.approvedScore().value());
        }
        return pending;
    }
}
