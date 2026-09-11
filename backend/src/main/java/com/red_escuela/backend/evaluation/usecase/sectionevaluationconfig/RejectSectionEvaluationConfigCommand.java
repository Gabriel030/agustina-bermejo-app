package com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig;

import com.red_escuela.backend.evaluation.dto.RejectSectionEvaluationConfigRequest;
import com.red_escuela.backend.identity.entity.Person;

public record RejectSectionEvaluationConfigCommand(Long sectionId, Person actor, RejectSectionEvaluationConfigRequest request) {
}
