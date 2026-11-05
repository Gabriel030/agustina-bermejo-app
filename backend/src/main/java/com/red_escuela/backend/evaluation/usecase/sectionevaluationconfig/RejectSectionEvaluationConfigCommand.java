package com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig;

import com.red_escuela.backend.evaluation.dto.RejectSectionEvaluationConfigRequest;

public record RejectSectionEvaluationConfigCommand(Long sectionId, RejectSectionEvaluationConfigRequest request) {
}
