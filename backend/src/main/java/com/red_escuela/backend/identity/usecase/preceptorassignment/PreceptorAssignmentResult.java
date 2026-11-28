package com.red_escuela.backend.identity.usecase.preceptorassignment;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.PreceptorAssignment;

public record PreceptorAssignmentResult(PreceptorAssignment assignment, Person person) {
}
