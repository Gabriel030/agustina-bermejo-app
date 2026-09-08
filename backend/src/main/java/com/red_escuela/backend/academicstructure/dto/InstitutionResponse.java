package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.Institution;

public record InstitutionResponse(Long id, String name) {

    public static InstitutionResponse from(Institution institution) {
        return new InstitutionResponse(institution.getId(), institution.getName());
    }
}
