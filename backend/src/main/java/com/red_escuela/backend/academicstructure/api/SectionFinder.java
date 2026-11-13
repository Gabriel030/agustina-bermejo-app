package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.exception.CycleNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SectionNotOpenForEnrollmentException;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.academicstructure.repository.AcademicOfferingCycleRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class SectionFinder {

    private final SubjectSectionRepository programSectionRepository;
    private final CourseSubjectSectionRepository courseSectionRepository;
    private final AcademicOfferingCycleRepository cycleRepository;

    public SectionFinder(SubjectSectionRepository programSectionRepository,
                                    CourseSubjectSectionRepository courseSectionRepository,
                                    AcademicOfferingCycleRepository cycleRepository) {
        this.programSectionRepository = programSectionRepository;
        this.courseSectionRepository = courseSectionRepository;
        this.cycleRepository = cycleRepository;
    }

    public List<CycleSectionRef> listSectionsOfCycle(Long cycleId) {
        var cycle = cycleRepository.findById(cycleId).orElseThrow(() -> new CycleNotFoundException(cycleId));
        if (cycle.getProgram() != null) {
            return programSectionRepository.findByAcademicOfferingCycleId(cycleId).stream()
                    .map(s -> new CycleSectionRef(SectionKind.PROGRAM, s.getId(), s.getName(), s.getCapacity()))
                    .toList();
        }
        return courseSectionRepository.findByAcademicOfferingCycleId(cycleId).stream()
                .map(s -> new CycleSectionRef(SectionKind.COURSE, s.getId(), s.getName(), s.getCapacity()))
                .toList();
    }

    public ProgramSectionView findProgramSection(Long id) {
        SubjectSection section = programSectionRepository.findById(id)
                .orElseThrow(() -> new SectionNotFoundException(id));
        return new ProgramSectionView(section.getId(), section.getSubject().getId(), section.getTerm().getId(),
                section.getCapacity(), section.getName());
    }

    public ProgramSectionView findOpenProgramSection(Long id) {
        SubjectSection section = programSectionRepository.findById(id)
                .orElseThrow(() -> new SectionNotFoundException(id));
        requireOpenForEnrollment(section);
        return new ProgramSectionView(section.getId(), section.getSubject().getId(), section.getTerm().getId(),
                section.getCapacity(), section.getName());
    }

    public boolean isProgramSectionOpenForEnrollment(Long id) {
        return programSectionRepository.findById(id).map(this::isOpenForEnrollment).orElse(false);
    }

    private void requireOpenForEnrollment(SubjectSection section) {
        if (!isOpenForEnrollment(section)) {
            throw new SectionNotOpenForEnrollmentException(section.getId());
        }
    }

    private boolean isOpenForEnrollment(SubjectSection section) {
        return section.getStatus() == StatusType.ACTIVE && section.isOfferingApproved();
    }

    public CourseSectionView findCourseSection(Long id) {
        CourseSubjectSection section = courseSectionRepository.findById(id)
                .orElseThrow(() -> new SectionNotFoundException(id));
        var courseSubject = section.getCourseSubject();
        return new CourseSectionView(section.getId(), courseSubject.getId(), courseSubject.getCourse().getId(),
                section.getTerm().getId(), section.getCapacity(), section.getName());
    }

    public SubjectSection findProgramSectionEntity(Long id) {
        return programSectionRepository.findById(id).orElseThrow(() -> new SectionNotFoundException(id));
    }

    public CourseSubjectSection findCourseSectionEntity(Long id) {
        return courseSectionRepository.findById(id).orElseThrow(() -> new SectionNotFoundException(id));
    }

    public boolean exists(SectionKind kind, Long id) {
        return switch (kind) {
            case PROGRAM -> programSectionRepository.existsById(id);
            case COURSE -> courseSectionRepository.existsById(id);
        };
    }

    public void requireExists(SectionKind kind, Long id) {
        if (!exists(kind, id)) {
            throw new SectionNotFoundException(id);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockProgramSection(Long id) {
        programSectionRepository.findByIdForUpdate(id).orElseThrow(() -> new SectionNotFoundException(id));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockCourseSection(Long id) {
        courseSectionRepository.findByIdForUpdate(id).orElseThrow(() -> new SectionNotFoundException(id));
    }

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

    public boolean isEvaluationSchemeAssigned(Long schemeId) {
        return programSectionRepository.existsByEvaluationSchemeId(schemeId)
                || courseSectionRepository.existsByEvaluationSchemeId(schemeId);
    }
}
