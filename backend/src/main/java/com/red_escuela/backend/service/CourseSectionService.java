package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.AssignInstructorRequest;
import com.red_escuela.backend.dto.CreateSectionRequest;
import com.red_escuela.backend.dto.UpdateSectionRequest;
import com.red_escuela.backend.entity.CourseSubject;
import com.red_escuela.backend.entity.CourseSubjectSection;
import com.red_escuela.backend.entity.CourseSubjectSectionInstructor;
import com.red_escuela.backend.entity.EvaluationScheme;
import com.red_escuela.backend.entity.Instructor;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.exception.CourseSubjectNotFoundException;
import com.red_escuela.backend.exception.InstructorAlreadyAssignedException;
import com.red_escuela.backend.exception.InstructorNotFoundException;
import com.red_escuela.backend.exception.SectionNotFoundException;
import com.red_escuela.backend.exception.TermNotFoundException;
import com.red_escuela.backend.repository.CourseSubjectRepository;
import com.red_escuela.backend.repository.CourseSubjectSectionInstructorRepository;
import com.red_escuela.backend.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.repository.InstructorRepository;
import com.red_escuela.backend.repository.TermRepository;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CourseSectionService {

    private final CourseSubjectSectionRepository sectionRepository;
    private final CourseSubjectRepository courseSubjectRepository;
    private final TermRepository termRepository;
    private final CourseSubjectSectionInstructorRepository sectionInstructorRepository;
    private final InstructorRepository instructorRepository;
    private final EntityManager entityManager;

    public CourseSectionService(
            CourseSubjectSectionRepository sectionRepository,
            CourseSubjectRepository courseSubjectRepository,
            TermRepository termRepository,
            CourseSubjectSectionInstructorRepository sectionInstructorRepository,
            InstructorRepository instructorRepository,
            EntityManager entityManager) {
        this.sectionRepository = sectionRepository;
        this.courseSubjectRepository = courseSubjectRepository;
        this.termRepository = termRepository;
        this.sectionInstructorRepository = sectionInstructorRepository;
        this.instructorRepository = instructorRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public CourseSubjectSection crearComision(Long courseSubjectId, CreateSectionRequest request) {
        CourseSubject courseSubject = courseSubjectRepository.findById(courseSubjectId)
                .orElseThrow(() -> new CourseSubjectNotFoundException(courseSubjectId));
        Term term = termRepository.findById(request.termId())
                .orElseThrow(() -> new TermNotFoundException(request.termId()));

        CourseSubjectSection section = new CourseSubjectSection();
        section.setCourseSubject(courseSubject);
        section.setTerm(term);
        section.setName(request.name());
        section.setSchedule(request.schedule());
        section.setCapacity(request.capacity());
        section.setStatus(StatusType.ACTIVE);
        section.setCreatedAt(LocalDateTime.now());
        if (request.evaluationSchemeId() != null) {
            section.setEvaluationScheme(entityManager.getReference(EvaluationScheme.class, request.evaluationSchemeId()));
        }
        return sectionRepository.save(section);
    }

    @Transactional
    public CourseSubjectSection editarComision(Long id, UpdateSectionRequest request) {
        CourseSubjectSection section = findOrThrow(id);
        if (request.name() != null) {
            section.setName(request.name());
        }
        if (request.schedule() != null) {
            section.setSchedule(request.schedule());
        }
        if (request.capacity() != null) {
            section.setCapacity(request.capacity());
        }
        if (request.evaluationSchemeId() != null) {
            section.setEvaluationScheme(entityManager.getReference(EvaluationScheme.class, request.evaluationSchemeId()));
        }
        return sectionRepository.save(section);
    }

    public List<CourseSubjectSection> listarPorModulo(Long courseSubjectId) {
        return sectionRepository.findByCourseSubjectId(courseSubjectId);
    }

    public CourseSubjectSection verDetalle(Long id) {
        return findOrThrow(id);
    }

    @Transactional
    public void desactivar(Long id) {
        CourseSubjectSection section = findOrThrow(id);
        section.setStatus(StatusType.INACTIVE);
        sectionRepository.save(section);
    }

    @Transactional
    public CourseSubjectSectionInstructor asignarDocente(Long sectionId, AssignInstructorRequest request) {
        CourseSubjectSection section = findOrThrow(sectionId);
        Instructor instructor = instructorRepository.findById(request.instructorId())
                .orElseThrow(() -> new InstructorNotFoundException(request.instructorId()));

        CourseSubjectSectionInstructor assignment = new CourseSubjectSectionInstructor();
        assignment.setSection(section);
        assignment.setInstructor(instructor);
        assignment.setRole(request.role());
        assignment.setStartAt(request.startAt());
        assignment.setEndAt(request.endAt());
        assignment.setHoursPerWeek(request.hoursPerWeek());
        assignment.setNotes(request.notes());

        try {
            return sectionInstructorRepository.saveAndFlush(assignment);
        } catch (DataIntegrityViolationException e) {
            throw new InstructorAlreadyAssignedException(request.instructorId(), sectionId);
        }
    }

    public List<CourseSubjectSectionInstructor> listarDocentes(Long sectionId) {
        return sectionInstructorRepository.findBySectionId(sectionId);
    }

    @Transactional
    public void quitarDocente(Long sectionId, Long instructorAssignmentId) {
        sectionInstructorRepository.deleteById(instructorAssignmentId);
    }

    private CourseSubjectSection findOrThrow(Long id) {
        return sectionRepository.findById(id).orElseThrow(() -> new SectionNotFoundException(id));
    }
}
