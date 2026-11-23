package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.academicstructure.api.SectionSlotView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ChangeSectionCandidateResponse(Long sectionId, String name, Integer capacity, long occupiedSeats, Long availableSeats,
        List<SectionSlotView> slots, boolean selectable, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) BlockReason blockReason,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String blockDetail,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String conflictingSubject) {

    public enum BlockReason { CURRENT_SECTION, SECTION_NOT_OPEN, OTHER_TERM, SECTION_FULL, SCHEDULE_OVERLAP }
}
