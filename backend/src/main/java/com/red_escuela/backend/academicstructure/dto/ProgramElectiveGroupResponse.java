package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.ProgramElectiveGroup;

public record ProgramElectiveGroupResponse(Long programId, Long electiveGroupId, Integer minRequired, Integer maxAllowed) {

    public static ProgramElectiveGroupResponse from(ProgramElectiveGroup entity) {
        return new ProgramElectiveGroupResponse(
                entity.getProgram().getId(), entity.getElectiveGroup().getId(),
                entity.getMinRequired(), entity.getMaxAllowed());
    }
}
