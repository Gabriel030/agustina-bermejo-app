package com.red_escuela.backend.academicstructure.usecase;

public interface SectionTeachingQueries {

    boolean teaches(SectionKind kind, Long sectionId, Long instructorId);

    void requireTeaches(SectionKind kind, Long sectionId, Long instructorId);

    boolean isLead(Long programSectionId, Long instructorId);
}
