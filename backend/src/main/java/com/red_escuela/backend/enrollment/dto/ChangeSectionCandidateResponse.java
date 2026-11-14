package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.academicstructure.api.SectionSlotView;

import java.util.List;

public record ChangeSectionCandidateResponse(Long sectionId, String name, Integer capacity, long occupiedSeats, Long availableSeats,
        List<SectionSlotView> slots, boolean selectable, BlockReason blockReason, String blockDetail, String conflictingSubject) {

    public enum BlockReason { CURRENT_SECTION, SECTION_NOT_OPEN, OTHER_TERM, SECTION_FULL, SCHEDULE_OVERLAP }
}
