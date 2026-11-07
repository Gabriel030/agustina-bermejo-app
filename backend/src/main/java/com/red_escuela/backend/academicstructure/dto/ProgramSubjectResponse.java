package com.red_escuela.backend.academicstructure.dto;

import io.swagger.v3.oas.annotations.media.Schema;

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
    private final String subjectStatus;
    @Schema(nullable = true) private final Integer weeklyHours;
    @Schema(nullable = true) private final Integer weeklyHoursLab;
    private final ProgramSubjectCategory category;
    @Schema(nullable = true) private final Integer curricularYear;
    @Schema(nullable = true) private final String curricularTerm;
    private final Integer credits;
    @Schema(nullable = true) private final Integer sortOrder;
    @Schema(nullable = true) private final Long electiveGroupId;
    @Schema(nullable = true) private final String status;

    public static ProgramSubjectResponse from(ProgramSubject ps) {
        return ProgramSubjectResponse.builder()
                .id(ps.getId())
                .programId(ps.getProgram().getId())
                .subjectId(ps.getSubject().getId())
                .subjectCode(ps.getSubject().getCode())
                .subjectName(ps.getSubject().getName())
                .subjectStatus(ps.getSubject().getStatus() == null ? null : ps.getSubject().getStatus().name())
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
