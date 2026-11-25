package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import java.util.List;

public interface ListMySectionEvaluationConfigsUseCase {
    List<SectionEvaluationConfigWithDeadline> list(Long instructorId);
}
