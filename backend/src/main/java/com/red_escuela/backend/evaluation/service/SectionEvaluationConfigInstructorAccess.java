package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.academicstructure.api.SectionInstructorFinder;
import com.red_escuela.backend.academicstructure.api.SectionTeachingFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.exception.NotOwnSectionException;
import com.red_escuela.backend.evaluation.dto.SectionInstructorSummary;
import com.red_escuela.backend.evaluation.exception.NotSectionLeadInstructorException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SectionEvaluationConfigInstructorAccess {

    private final SectionTeachingFinder sectionTeachingFinder;
    private final SectionInstructorFinder sectionInstructorFinder;

    public SectionEvaluationConfigInstructorAccess(SectionTeachingFinder sectionTeachingFinder,
                                                   SectionInstructorFinder sectionInstructorFinder) {
        this.sectionTeachingFinder = sectionTeachingFinder;
        this.sectionInstructorFinder = sectionInstructorFinder;
    }

    public void requireAssignedToSection(Long sectionId, Long instructorId) {
        sectionTeachingFinder.requireTeaches(SectionKind.PROGRAM, sectionId, instructorId);
    }

    public void requireLeadInstructor(Long sectionId, Long instructorId) {
        requireAssignedToSection(sectionId, instructorId);
        if (!isLeadInstructor(sectionId, instructorId)) {
            throw new NotSectionLeadInstructorException(instructorId, sectionId);
        }
    }

    public boolean isLeadInstructor(Long sectionId, Long instructorId) {
        return sectionTeachingFinder.isLead(sectionId, instructorId);
    }

    public List<SectionInstructorSummary> listInstructors(Long sectionId) {
        return sectionInstructorFinder.findProgramSectionInstructors(sectionId).stream()
                .map(SectionInstructorSummary::from)
                .toList();
    }

    public Long leadInstructorPersonId(Long sectionId) {
        return sectionInstructorFinder.findProgramSectionLeadPersonId(sectionId)
                .orElseThrow(() -> new NotOwnSectionException(null, sectionId));
    }
}
