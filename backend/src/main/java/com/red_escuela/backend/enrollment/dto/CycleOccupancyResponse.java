package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.academicstructure.domain.SectionKind;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record CycleOccupancyResponse(Long cycleId, List<SectionOccupancy> sections) {

    public record SectionOccupancy(Long sectionId, SectionKind kind, String name, @Schema(nullable = true) Integer capacity,
                                   long enrolled, @Schema(nullable = true) Long available, long waitlisted) {
    }
}
