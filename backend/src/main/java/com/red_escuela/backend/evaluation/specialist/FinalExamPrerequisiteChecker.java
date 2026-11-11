package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.enrollment.api.ProgramPrerequisiteEvaluator;
import com.red_escuela.backend.evaluation.exception.FinalExamPrerequisitesNotApprovedException;
import org.springframework.stereotype.Component;

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
                .findUnmetForApprovalOrEmptyOutsidePlan(programEnrollmentId, subjectId).stream()
                .<Map<String, Object>>map(assessment -> Map.of(
                        "subjectId", assessment.subjectId(),
                        "subjectName", assessment.subjectName(),
                        "standing", assessment.standingName()))
                .toList();
        if (!pending.isEmpty()) {
            throw new FinalExamPrerequisitesNotApprovedException(enrollmentId, pending);
        }
    }
}
