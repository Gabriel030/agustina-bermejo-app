package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.dto.AssignInstructorRequest;
import com.red_escuela.backend.academicstructure.dto.CreateSectionRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateSectionRequest;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSectionInstructor;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.CourseSubjectNotFoundException;
import com.red_escuela.backend.evaluation.exception.EvaluationSchemeNotFoundException;
import com.red_escuela.backend.academicstructure.exception.InstructorAlreadyAssignedException;
import com.red_escuela.backend.academicstructure.exception.InstructorNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SectionInstructorNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.exception.TermNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionInstructorRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.InstructorRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
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
    public CourseSubjectSection create(Long courseSubjectId, CreateSectionRequest request) {
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
            section.setEvaluationScheme(resolveEvaluationScheme(request.evaluationSchemeId()));
        }
        return sectionRepository.save(section);
    }

    @Transactional
    public CourseSubjectSection update(Long id, UpdateSectionRequest request) {
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
            section.setEvaluationScheme(resolveEvaluationScheme(request.evaluationSchemeId()));
        }
        return sectionRepository.save(section);
    }

    public List<CourseSubjectSection> listByCourseSubject(Long courseSubjectId, StatusType status) {
        return status == null
                ? sectionRepository.findByCourseSubjectId(courseSubjectId)
                : sectionRepository.findByCourseSubjectIdAndStatus(courseSubjectId, status);
    }

    public CourseSubjectSection get(Long id) {
        return findOrThrow(id);
    }

    @Transactional
    public void deactivate(Long id) {
        CourseSubjectSection section = findOrThrow(id);
        section.setStatus(StatusType.INACTIVE);
        sectionRepository.save(section);
    }

    @Transactional
    public void reactivate(Long id) {
        CourseSubjectSection section = findOrThrow(id);
        section.setStatus(StatusType.ACTIVE);
        sectionRepository.save(section);
    }

    @Transactional
    public CourseSubjectSectionInstructor assignInstructor(Long sectionId, AssignInstructorRequest request) {
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

    public List<CourseSubjectSectionInstructor> listInstructors(Long sectionId) {
        return sectionInstructorRepository.findBySectionId(sectionId);
    }

    @Transactional
    public void removeInstructor(Long sectionId, Long instructorAssignmentId) {
        CourseSubjectSectionInstructor assignment = sectionInstructorRepository.findById(instructorAssignmentId)
                .filter(a -> a.getSection().getId().equals(sectionId))
                .orElseThrow(() -> new SectionInstructorNotFoundException(instructorAssignmentId));
        sectionInstructorRepository.delete(assignment);
    }

    private CourseSubjectSection findOrThrow(Long id) {
        return sectionRepository.findById(id).orElseThrow(() -> new SectionNotFoundException(id));
    }

    private EvaluationScheme resolveEvaluationScheme(Long id) {
        EvaluationScheme scheme = entityManager.find(EvaluationScheme.class, id);
        if (scheme == null) {
            throw new EvaluationSchemeNotFoundException(id);
        }
        return scheme;
    }
}
