package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCategory;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record ProgramCurriculumEntryView(Long programSubjectId, Long subjectId, String subjectCode, String subjectName,
                                         int credits, Integer curricularYear, CurricularTerm curricularTerm,
                                         ProgramSubjectCategory category, Long electiveGroupId) {

    public boolean isElective() {
        return electiveGroupId != null;
    }
}
