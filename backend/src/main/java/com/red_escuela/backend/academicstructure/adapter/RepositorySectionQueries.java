package com.red_escuela.backend.academicstructure.adapter;

import com.red_escuela.backend.academicstructure.dto.CourseSectionView;
import com.red_escuela.backend.academicstructure.dto.ProgramSectionView;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.academicstructure.usecase.SectionKind;
import com.red_escuela.backend.academicstructure.usecase.SectionQueries;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class RepositorySectionQueries implements SectionQueries {

    private final SubjectSectionRepository programSectionRepository;
    private final CourseSubjectSectionRepository courseSectionRepository;

    public RepositorySectionQueries(SubjectSectionRepository programSectionRepository,
                                    CourseSubjectSectionRepository courseSectionRepository) {
        this.programSectionRepository = programSectionRepository;
        this.courseSectionRepository = courseSectionRepository;
    }

    @Override
    public ProgramSectionView findProgramSection(Long id) {
        SubjectSection section = programSectionRepository.findById(id)
                .orElseThrow(() -> new SectionNotFoundException(id));
        return new ProgramSectionView(section.getId(), section.getSubject().getId(), section.getTerm().getId(),
                section.getCapacity(), section.getName());
    }

    @Override
    public CourseSectionView findCourseSection(Long id) {
        CourseSubjectSection section = courseSectionRepository.findById(id)
                .orElseThrow(() -> new SectionNotFoundException(id));
        var courseSubject = section.getCourseSubject();
        return new CourseSectionView(section.getId(), courseSubject.getId(), courseSubject.getCourse().getId(),
                section.getTerm().getId(), section.getCapacity(), section.getName());
    }

    @Override
    public boolean exists(SectionKind kind, Long id) {
        return switch (kind) {
            case PROGRAM -> programSectionRepository.existsById(id);
            case COURSE -> courseSectionRepository.existsById(id);
        };
    }

    @Override
    public void requireExists(SectionKind kind, Long id) {
        if (!exists(kind, id)) {
            throw new SectionNotFoundException(id);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockProgramSection(Long id) {
        programSectionRepository.findByIdForUpdate(id).orElseThrow(() -> new SectionNotFoundException(id));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockCourseSection(Long id) {
        courseSectionRepository.findByIdForUpdate(id).orElseThrow(() -> new SectionNotFoundException(id));
    }

    @Override
    public Optional<Long> findEvaluationSchemeId(SectionKind kind, Long id) {
        return switch (kind) {
            case PROGRAM -> {
                requireExists(programSectionRepository.existsById(id), id);
                yield programSectionRepository.findEvaluationSchemeIdById(id);
            }
            case COURSE -> {
                requireExists(courseSectionRepository.existsById(id), id);
                yield courseSectionRepository.findEvaluationSchemeIdById(id);
            }
        };
    }

    private void requireExists(boolean exists, Long id) {
        if (!exists) {
            throw new SectionNotFoundException(id);
        }
    }

    @Override
    public boolean isEvaluationSchemeAssigned(Long schemeId) {
        return programSectionRepository.existsByEvaluationSchemeId(schemeId)
                || courseSectionRepository.existsByEvaluationSchemeId(schemeId);
    }
}
