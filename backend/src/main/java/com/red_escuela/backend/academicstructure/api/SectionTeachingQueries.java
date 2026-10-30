package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
public interface SectionTeachingQueries {

    boolean teaches(SectionKind kind, Long sectionId, Long instructorId);

    void requireTeaches(SectionKind kind, Long sectionId, Long instructorId);

    boolean isLead(Long programSectionId, Long instructorId);
}
