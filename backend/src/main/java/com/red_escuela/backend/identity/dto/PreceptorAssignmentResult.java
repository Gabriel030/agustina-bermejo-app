package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.PreceptorAssignment;

public record PreceptorAssignmentResult(PreceptorAssignment assignment, Person person) {
}
