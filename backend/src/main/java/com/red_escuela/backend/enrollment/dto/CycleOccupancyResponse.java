package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.academicstructure.domain.SectionKind;

import java.util.List;

public record CycleOccupancyResponse(Long cycleId, List<SectionOccupancy> sections) {

    public record SectionOccupancy(Long sectionId, SectionKind kind, String name, Integer capacity, long enrolled,
                                   Long available, long waitlisted) {
    }
}
