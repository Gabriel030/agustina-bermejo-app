package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.entity.SectionGradeItemPublication;
import com.red_escuela.backend.evaluation.exception.PendingCourseworkException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CourseworkApprovalChecker {

    private final ApprovedCourseworkCountFinder courseworkCountFinder;

    public CourseworkApprovalChecker(ApprovedCourseworkCountFinder courseworkCountFinder) {
        this.courseworkCountFinder = courseworkCountFinder;
    }

    public void requireApprovedBeforeExam(
            SectionKind kind, Long sectionId, EvaluationScheme scheme, List<GradeItem> enrollmentItems) {
        int expected = courseworkCountFinder.findExpectedCount(kind, sectionId);
        if (expected == 0) {
            return;
        }
        long approved = enrollmentItems.stream()
                .filter(item -> item.getType() == GradeItemType.TP)
                .filter(item -> isApproved(item, scheme))
                .map(item -> SectionGradeItemPublication.sequenceKey(item.getSequenceNumber()))
                .distinct()
                .count();
        if (approved < expected) {
            throw new PendingCourseworkException(expected - (int) approved);
        }
    }

    private boolean isApproved(GradeItem item, EvaluationScheme scheme) {
        if (item.getScore() == null) {
            return false;
        }
        return scheme.getMinRegularGrade() == null || item.getScore().meets(scheme.getMinRegularGrade());
    }
}
