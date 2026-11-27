package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.SectionInstructorFinder;
import com.red_escuela.backend.academicstructure.api.SectionInstructorView;
import com.red_escuela.backend.academicstructure.api.SectionTeachingFinder;
import com.red_escuela.backend.academicstructure.exception.NotOwnSectionException;
import com.red_escuela.backend.evaluation.domain.SectionRef;
import com.red_escuela.backend.evaluation.dto.SectionInstructorSummary;
import com.red_escuela.backend.evaluation.exception.NotSectionLeadInstructorException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SectionEvaluationConfigInstructorAccessChecker {

    private final SectionTeachingFinder sectionTeachingFinder;
    private final SectionInstructorFinder sectionInstructorFinder;

    public SectionEvaluationConfigInstructorAccessChecker(SectionTeachingFinder sectionTeachingFinder,
                                                   SectionInstructorFinder sectionInstructorFinder) {
        this.sectionTeachingFinder = sectionTeachingFinder;
        this.sectionInstructorFinder = sectionInstructorFinder;
    }

    public void requireAssignedToSection(SectionRef section, Long instructorId) {
        sectionTeachingFinder.requireTeaches(section.kind(), section.id(), instructorId);
    }

    public void requireLeadInstructor(SectionRef section, Long instructorId) {
        requireAssignedToSection(section, instructorId);
        if (!isLeadInstructor(section, instructorId)) {
            throw new NotSectionLeadInstructorException(instructorId, section.id());
        }
    }

    public boolean isLeadInstructor(SectionRef section, Long instructorId) {
        return sectionTeachingFinder.isLead(section.kind(), section.id(), instructorId);
    }

    public List<SectionInstructorSummary> listInstructors(SectionRef section) {
        List<SectionInstructorView> instructors = section.isCourse()
                ? sectionInstructorFinder.findCourseSectionInstructors(section.id())
                : sectionInstructorFinder.findProgramSectionInstructors(section.id());
        return instructors.stream()
                .map(SectionInstructorSummary::from)
                .toList();
    }

    public Long leadInstructorPersonId(SectionRef section) {
        Optional<Long> leadPersonId = section.isCourse()
                ? sectionInstructorFinder.findCourseSectionLeadPersonId(section.id())
                : sectionInstructorFinder.findProgramSectionLeadPersonId(section.id());
        return leadPersonId.orElseThrow(() -> new NotOwnSectionException(null, section.id()));
    }
}
