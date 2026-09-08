package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.SubjectRecurrence;

// Sin programId ni subjectId: una entrada de plan es la combinación fija (carrera,
// materia) desde el alta — ver ProgramSubjectService.editarMateria.
public record UpdateProgramSubjectRequest(
        Integer curricularYear, CurricularTerm curricularTerm, Integer credits,
        SubjectRecurrence subjectRecurrence, Integer sortOrder, Long electiveGroupId) {
}
