package com.red_escuela.backend.evaluation.dto;

import jakarta.validation.constraints.Size;

public record TeacherObservationRequest(@Size(max = 500) String observation) {
}
