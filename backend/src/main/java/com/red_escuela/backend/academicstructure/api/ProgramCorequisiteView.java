package com.red_escuela.backend.academicstructure.api;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record ProgramCorequisiteView(Long corequisiteProgramSubjectId, Long corequisiteSubjectId,
                                     String corequisiteSubjectCode, String corequisiteSubjectName) {
}
