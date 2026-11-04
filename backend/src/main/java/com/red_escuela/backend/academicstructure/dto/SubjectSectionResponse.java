package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.domain.SectionShift;
import com.red_escuela.backend.shared.domain.Percentage;

public record SubjectSectionResponse(
        Long id, String name, Long subjectId, String subjectName, Long termId,
        String schedule, Integer capacity, String status, Percentage maxAbsencePercent,
        SectionShift shift, String code, String description) {

    public static SubjectSectionResponse from(SubjectSection section) {
        return new SubjectSectionResponse(
                section.getId(), section.getName(), section.getSubject().getId(), section.getSubject().getName(),
                section.getTerm().getId(), section.getSchedule(), section.getCapacity(),
                section.getStatus() == null ? null : section.getStatus().name(),
                section.getMaxAbsenceRate(),
                section.getShift(), section.getCode(), section.getDescription());
    }
}
