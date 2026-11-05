package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.shared.domain.StatusType;

import java.time.LocalDateTime;
import java.util.List;

public record StaffAssignmentResponse(
        Long sectionId,
        SectionKind kind,
        String subjectName,
        String sectionName,
        InstructorRole role,
        Integer hoursPerWeek,
        String termDescription,
        LocalDateTime termStartDate,
        LocalDateTime termEndDate,
        StatusType sectionStatus,
        List<StaffAssignmentSlotResponse> slots
) {
}
