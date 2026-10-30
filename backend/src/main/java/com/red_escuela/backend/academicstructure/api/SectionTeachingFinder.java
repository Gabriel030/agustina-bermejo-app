package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.exception.NotOwnSectionException;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionInstructorRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionInstructorRepository;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class SectionTeachingFinder {

    private final SubjectSectionInstructorRepository programInstructorRepository;
    private final CourseSubjectSectionInstructorRepository courseInstructorRepository;

    public SectionTeachingFinder(SubjectSectionInstructorRepository programInstructorRepository,
                                            CourseSubjectSectionInstructorRepository courseInstructorRepository) {
        this.programInstructorRepository = programInstructorRepository;
        this.courseInstructorRepository = courseInstructorRepository;
    }

    public boolean teaches(SectionKind kind, Long sectionId, Long instructorId) {
        return switch (kind) {
            case PROGRAM -> programInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId);
            case COURSE -> courseInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId);
        };
    }

    public void requireTeaches(SectionKind kind, Long sectionId, Long instructorId) {
        if (!teaches(kind, sectionId, instructorId)) {
            throw new NotOwnSectionException(instructorId, sectionId);
        }
    }

    public boolean isLead(Long programSectionId, Long instructorId) {
        return programInstructorRepository.existsBySectionIdAndInstructorIdAndRole(
                programSectionId, instructorId, InstructorRole.LEAD);
    }
}
