package com.red_escuela.backend.academicstructure.api;

import java.util.Collection;
import java.util.Set;

public interface SectionEvaluationApprovalPort {
    Set<Long> programSectionIdsWithApprovedEvaluation(Collection<Long> programSectionIds);

    Set<Long> courseSectionIdsWithApprovedEvaluation(Collection<Long> courseSectionIds);
}
