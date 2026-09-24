package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCategory;

public record ProgramSubjectResponse(
        Long id, Long programId, Long subjectId, String subjectCode, String subjectName,
        Integer weeklyHours, Integer weeklyHoursLab,
        ProgramSubjectCategory category,
        Integer curricularYear, String curricularTerm,
        Integer credits, Integer sortOrder, Long electiveGroupId, String status) {

    public static ProgramSubjectResponse from(ProgramSubject ps) {
        return new ProgramSubjectResponse(
                ps.getId(),
                ps.getProgram().getId(),
                ps.getSubject().getId(),
                ps.getSubject().getCode(),
                ps.getSubject().getName(),
                ps.getSubject().getWeeklyHours(),
                ps.getSubject().getWeeklyHoursLab(),
                ps.getCategory(),
                ps.getCurricularYear(),
                ps.getCurricularTerm() == null ? null : ps.getCurricularTerm().name(),
                ps.getCredits(),
                ps.getSortOrder(),
                ps.getElectiveGroup() == null ? null : ps.getElectiveGroup().getId(),
                ps.getStatus() == null ? null : ps.getStatus().name());
    }
}
