package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record ProgramSubjectView(Long id, Long subjectId, String subjectName, Integer credits,
                                 Integer curricularYear, CurricularTerm curricularTerm, Long electiveGroupId) {

    public boolean isElective() {
        return electiveGroupId != null;
    }
}
