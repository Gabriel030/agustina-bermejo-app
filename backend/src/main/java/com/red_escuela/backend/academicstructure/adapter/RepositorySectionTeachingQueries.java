package com.red_escuela.backend.academicstructure.adapter;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.exception.NotOwnSectionException;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionInstructorRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionInstructorRepository;
import com.red_escuela.backend.academicstructure.usecase.SectionKind;
import com.red_escuela.backend.academicstructure.usecase.SectionTeachingQueries;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RepositorySectionTeachingQueries implements SectionTeachingQueries {

    private final SubjectSectionInstructorRepository programInstructorRepository;
    private final CourseSubjectSectionInstructorRepository courseInstructorRepository;

    public RepositorySectionTeachingQueries(SubjectSectionInstructorRepository programInstructorRepository,
                                            CourseSubjectSectionInstructorRepository courseInstructorRepository) {
        this.programInstructorRepository = programInstructorRepository;
        this.courseInstructorRepository = courseInstructorRepository;
    }

    @Override
    public boolean teaches(SectionKind kind, Long sectionId, Long instructorId) {
        return switch (kind) {
            case PROGRAM -> programInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId);
            case COURSE -> courseInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId);
        };
    }

    @Override
    public void requireTeaches(SectionKind kind, Long sectionId, Long instructorId) {
        if (!teaches(kind, sectionId, instructorId)) {
            throw new NotOwnSectionException(instructorId, sectionId);
        }
    }

    @Override
    public boolean isLead(Long programSectionId, Long instructorId) {
        return programInstructorRepository.existsBySectionIdAndInstructorIdAndRole(
                programSectionId, instructorId, InstructorRole.LEAD);
    }
}
