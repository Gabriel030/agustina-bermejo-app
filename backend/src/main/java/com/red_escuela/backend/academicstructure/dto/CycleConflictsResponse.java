package com.red_escuela.backend.academicstructure.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record CycleConflictsResponse(boolean hasConflicts, List<Conflict> conflicts) {

    public record Conflict(
            String type,
            String dayOfWeek,
            String startTime,
            String endTime,
            Long classroomId,
            String classroomName,
            @Schema(nullable = true) Long instructorId,
            @Schema(nullable = true) String instructorName,
            Side ownSection,
            Side otherSection
    ) {}

    public record Side(Long sectionId, String sectionName, String subjectName, @Schema(nullable = true) Long cycleId) {}
}
