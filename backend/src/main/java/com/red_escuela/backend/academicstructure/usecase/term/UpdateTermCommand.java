package com.red_escuela.backend.academicstructure.usecase.term;

import com.red_escuela.backend.academicstructure.dto.UpdateTermRequest;

public record UpdateTermCommand(Long id, UpdateTermRequest request) {
}
