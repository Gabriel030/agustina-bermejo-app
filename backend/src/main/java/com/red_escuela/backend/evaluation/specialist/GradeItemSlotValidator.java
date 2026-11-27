package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.exception.GradeItemOutsideConfiguredSlotsException;
import org.springframework.stereotype.Component;

@Component
public class GradeItemSlotValidator {

    private final ApprovedCourseworkCountFinder courseworkCountFinder;

    public GradeItemSlotValidator(ApprovedCourseworkCountFinder courseworkCountFinder) {
        this.courseworkCountFinder = courseworkCountFinder;
    }

    public void requireWithinConfiguredSlots(
            SectionKind kind, Long sectionId, EvaluationScheme scheme, GradeItemType type, Integer sequenceNumber) {
        if (type == GradeItemType.EXAM) {
            requireWithin(type, sequenceNumber, scheme.getExamCountExpected() == null ? 0 : scheme.getExamCountExpected());
        } else if (type == GradeItemType.TP && kind == SectionKind.PROGRAM) {
            requireWithin(type, sequenceNumber, courseworkCountFinder.findExpectedCount(kind, sectionId));
        }
    }

    private void requireWithin(GradeItemType type, Integer sequenceNumber, int configuredCount) {
        if (sequenceNumber == null || sequenceNumber < 1 || sequenceNumber > configuredCount) {
            throw new GradeItemOutsideConfiguredSlotsException(type, sequenceNumber, configuredCount);
        }
    }
}
