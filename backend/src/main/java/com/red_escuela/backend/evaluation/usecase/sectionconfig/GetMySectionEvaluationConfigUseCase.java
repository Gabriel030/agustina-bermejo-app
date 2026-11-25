package com.red_escuela.backend.evaluation.usecase.sectionconfig;

public interface GetMySectionEvaluationConfigUseCase {
    SectionEvaluationConfigWithDeadline get(GetMySectionEvaluationConfigCommand command);
}
