package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.identity.api.PreceptorAssignmentTargetPort;
import org.springframework.stereotype.Service;

@Service
public class PreceptorAssignmentTargetLookup implements PreceptorAssignmentTargetPort {

    private final SectionFinder sectionFinder;

    public PreceptorAssignmentTargetLookup(SectionFinder sectionFinder) {
        this.sectionFinder = sectionFinder;
    }

    @Override
    public boolean subjectSectionExists(Long subjectSectionId) {
        return sectionFinder.exists(SectionKind.PROGRAM, subjectSectionId);
    }

    @Override
    public boolean courseSectionExists(Long courseSectionId) {
        return sectionFinder.exists(SectionKind.COURSE, courseSectionId);
    }
}
