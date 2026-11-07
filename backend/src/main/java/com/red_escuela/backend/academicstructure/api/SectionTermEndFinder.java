package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@Transactional(readOnly = true)
public class SectionTermEndFinder {

    private final SectionFinder sectionFinder;
    private final CourseSectionTermEndResolver courseSectionTermEndResolver;

    public SectionTermEndFinder(SectionFinder sectionFinder, CourseSectionTermEndResolver courseSectionTermEndResolver) {
        this.sectionFinder = sectionFinder;
        this.courseSectionTermEndResolver = courseSectionTermEndResolver;
    }

    public LocalDate termEndDate(SectionKind kind, Long sectionId) {
        return switch (kind) {
            case PROGRAM -> programTermEndDate(sectionFinder.findProgramSectionEntity(sectionId));
            case COURSE -> courseSectionTermEndResolver.termEndDate(sectionFinder.findCourseSectionEntity(sectionId));
        };
    }

    private LocalDate programTermEndDate(SubjectSection section) {
        AcademicOfferingCycle cycle = section.getAcademicOfferingCycle();
        return (cycle != null ? cycle.getTerm() : section.getTerm()).getEndDate().toLocalDate();
    }
}
