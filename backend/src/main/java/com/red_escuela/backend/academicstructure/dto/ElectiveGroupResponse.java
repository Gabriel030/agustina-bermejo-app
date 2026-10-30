package com.red_escuela.backend.academicstructure.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.academicstructure.entity.ElectiveGroup;

public record ElectiveGroupResponse(Long id, String name, @Schema(nullable = true) String description) {

    public static ElectiveGroupResponse from(ElectiveGroup group) {
        return new ElectiveGroupResponse(group.getId(), group.getName(), group.getDescription());
    }
}
