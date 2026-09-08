package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.ElectiveGroup;

public record ElectiveGroupResponse(Long id, String name, String description) {

    public static ElectiveGroupResponse from(ElectiveGroup group) {
        return new ElectiveGroupResponse(group.getId(), group.getName(), group.getDescription());
    }
}
