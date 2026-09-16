package com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig;

import com.red_escuela.backend.evaluation.dto.UpdateSectionEvaluationConfigRequest;

public record SaveSectionEvaluationConfigDraftCommand(Long sectionId, Long instructorId, UpdateSectionEvaluationConfigRequest request) {
}
