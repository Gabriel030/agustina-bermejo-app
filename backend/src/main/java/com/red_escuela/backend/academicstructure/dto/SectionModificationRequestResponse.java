package com.red_escuela.backend.academicstructure.dto;

import java.util.List;

public record SectionModificationRequestResponse(
        Long sectionId,
        String sectionName,
        String subjectName,
        String offeringName,
        Long cycleId,
        String requestedByName,
        String requestedAt,
        boolean replacesSchedule,
        List<CycleSectionResponse.SlotInfo> currentSlots,
        List<CycleSectionResponse.SlotInfo> proposedSlots) {
}
