package com.red_escuela.backend.academicstructure.api;

public interface SectionEnrollmentActivityPort {
    boolean hasLiveEnrollmentsInProgramSection(Long sectionId);

    boolean hasLiveEnrollmentsInCourseSection(Long sectionId);

    boolean hasLiveEnrollmentsInTerm(Long termId);
}
