package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.ElectiveGroup;

public record ElectiveGroupResponse(Long id, String name, String description) {

    public static ElectiveGroupResponse from(ElectiveGroup group) {
        return new ElectiveGroupResponse(group.getId(), group.getName(), group.getDescription());
    }
}
