package com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig;

import com.red_escuela.backend.identity.entity.Person;

public record ApproveSectionEvaluationConfigCommand(Long sectionId, Person actor) {
}
