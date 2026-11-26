package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;

public interface SubmitSectionEvaluationConfigForApprovalUseCase {
    SectionEvaluationConfigResponse submit(SubmitSectionEvaluationConfigForApprovalCommand command);
}
