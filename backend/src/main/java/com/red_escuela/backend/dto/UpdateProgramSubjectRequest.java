package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.CurricularTerm;
import com.red_escuela.backend.entity.SubjectRecurrence;

// Sin programId ni subjectId: una entrada de plan es la combinación fija (carrera,
// materia) desde el alta — ver ProgramSubjectService.updateSubject.
public record UpdateProgramSubjectRequest(
        Integer curricularYear, CurricularTerm curricularTerm, Integer credits,
        SubjectRecurrence subjectRecurrence, Integer sortOrder, Long electiveGroupId) {
}
