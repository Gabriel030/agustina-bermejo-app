package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;

public record ProgramSubjectResponse(
        Long id, Long programId, Long subjectId, String subjectCode, String subjectName,
        Integer curricularYear, CurricularTerm curricularTerm, Integer credits,
        Long electiveGroupId, String status) {

    public static ProgramSubjectResponse from(ProgramSubject programSubject) {
        return new ProgramSubjectResponse(
                programSubject.getId(),
                programSubject.getProgram().getId(),
                programSubject.getSubject().getId(),
                programSubject.getSubject().getCode(),
                programSubject.getSubject().getName(),
                programSubject.getCurricularYear(),
                programSubject.getCurricularTerm(),
                programSubject.getCredits(),
                programSubject.getElectiveGroup() == null ? null : programSubject.getElectiveGroup().getId(),
                programSubject.getStatus() == null ? null : programSubject.getStatus().name());
    }
}
