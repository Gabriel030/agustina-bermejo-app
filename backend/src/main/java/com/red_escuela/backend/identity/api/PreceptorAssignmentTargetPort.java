package com.red_escuela.backend.identity.api;

public interface PreceptorAssignmentTargetPort {
    boolean subjectSectionExists(Long subjectSectionId);

    boolean courseSectionExists(Long courseSectionId);
}
