package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.evaluation.entity.SectionGradeItemPublication;
import com.red_escuela.backend.evaluation.exception.PendingCourseworkException;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CourseworkApprovalChecker {

    private final SectionEvaluationConfigRepository configRepository;

    public CourseworkApprovalChecker(SectionEvaluationConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    public void requireApprovedBeforeExam(
            SectionKind kind, Long sectionId, EvaluationScheme scheme, List<GradeItem> enrollmentItems) {
        int expected = expectedCourseworkCount(kind, sectionId, scheme);
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

    private int expectedCourseworkCount(SectionKind kind, Long sectionId, EvaluationScheme scheme) {
        int fromScheme = scheme.getTpCountExpected() == null ? 0 : scheme.getTpCountExpected();
        if (fromScheme > 0 || kind != SectionKind.PROGRAM) {
            return fromScheme;
        }
        return configRepository.findBySubjectSectionId(sectionId)
                .filter(config -> config.getStatus() == SectionEvaluationConfigStatus.APPROVED)
                .map(SectionEvaluationConfig::getTpCount)
                .orElse(0);
    }

    private boolean isApproved(GradeItem item, EvaluationScheme scheme) {
        if (item.getScore() == null) {
            return false;
        }
        return scheme.getMinRegularGrade() == null || item.getScore().meets(scheme.getMinRegularGrade());
    }
}
