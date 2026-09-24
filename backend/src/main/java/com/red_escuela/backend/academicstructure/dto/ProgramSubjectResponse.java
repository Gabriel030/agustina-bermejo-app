package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCategory;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProgramSubjectResponse {
    private final Long id;
    private final Long programId;
    private final Long subjectId;
    private final String subjectCode;
    private final String subjectName;
    private final Integer weeklyHours;
    private final Integer weeklyHoursLab;
    private final ProgramSubjectCategory category;
    private final Integer curricularYear;
    private final String curricularTerm;
    private final Integer credits;
    private final Integer sortOrder;
    private final Long electiveGroupId;
    private final String status;

    public static ProgramSubjectResponse from(ProgramSubject ps) {
        return ProgramSubjectResponse.builder()
                .id(ps.getId())
                .programId(ps.getProgram().getId())
                .subjectId(ps.getSubject().getId())
                .subjectCode(ps.getSubject().getCode())
                .subjectName(ps.getSubject().getName())
                .weeklyHours(ps.getSubject().getWeeklyHours())
                .weeklyHoursLab(ps.getSubject().getWeeklyHoursLab())
                .category(ps.getCategory())
                .curricularYear(ps.getCurricularYear())
                .curricularTerm(ps.getCurricularTerm() == null ? null : ps.getCurricularTerm().name())
                .credits(ps.getCredits())
                .sortOrder(ps.getSortOrder())
                .electiveGroupId(ps.getElectiveGroup() == null ? null : ps.getElectiveGroup().getId())
                .status(ps.getStatus() == null ? null : ps.getStatus().name())
                .build();
    }
}
