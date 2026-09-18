package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.dto.AssignInstructorRequest;
import com.red_escuela.backend.academicstructure.dto.CreateSectionRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateSectionRequest;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.evaluation.exception.EvaluationSchemeNotFoundException;
import com.red_escuela.backend.academicstructure.exception.InstructorAlreadyAssignedException;
import com.red_escuela.backend.academicstructure.exception.InstructorNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SectionInstructorNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SubjectNotFoundException;
import com.red_escuela.backend.academicstructure.exception.TermNotFoundException;
import com.red_escuela.backend.academicstructure.repository.InstructorRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionInstructorRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import com.red_escuela.backend.evaluation.service.SectionEvaluationConfigDraftInitializer;

@Service
public class SubjectSectionService {
    private final SubjectSectionRepository sectionRepository;
    private final SubjectRepository subjectRepository;
    private final TermRepository termRepository;
    private final SubjectSectionInstructorRepository sectionInstructorRepository;
    private final InstructorRepository instructorRepository;
    private final EntityManager entityManager;
    private final SectionEvaluationConfigDraftInitializer sectionEvaluationConfigDraftInitializer;

    public SubjectSectionService(
            SubjectSectionRepository sectionRepository,
            SubjectRepository subjectRepository,
            TermRepository termRepository,
            SubjectSectionInstructorRepository sectionInstructorRepository,
            InstructorRepository instructorRepository,
            EntityManager entityManager,
            SectionEvaluationConfigDraftInitializer sectionEvaluationConfigDraftInitializer) {
        this.sectionRepository = sectionRepository;
        this.subjectRepository = subjectRepository;
        this.termRepository = termRepository;
        this.sectionInstructorRepository = sectionInstructorRepository;
        this.instructorRepository = instructorRepository;
        this.entityManager = entityManager;
        this.sectionEvaluationConfigDraftInitializer = sectionEvaluationConfigDraftInitializer;
    }

    @Transactional
    public SubjectSection create(Long subjectId, CreateSectionRequest request) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new SubjectNotFoundException(subjectId));
        Term term = termRepository.findById(request.termId())
                .orElseThrow(() -> new TermNotFoundException(request.termId()));

        SubjectSection section = new SubjectSection();
        section.setSubject(subject);
        section.setTerm(term);
        section.setName(request.name());
        section.setSchedule(request.schedule());
        section.setCapacity(request.capacity());
        section.setStatus(StatusType.ACTIVE);
        section.setCreatedAt(LocalDateTime.now());
        if (request.evaluationSchemeId() != null) {
            section.setEvaluationScheme(resolveEvaluationScheme(request.evaluationSchemeId()));
        }
        SubjectSection guardada = sectionRepository.save(section);
        sectionEvaluationConfigDraftInitializer.createInitialDraft(guardada);
        return guardada;
    }

    @Transactional
    public SubjectSection update(Long id, UpdateSectionRequest request) {
        SubjectSection section = findOrThrow(id);
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

    public List<SubjectSection> listBySubject(Long subjectId, StatusType status) {
        return status == null
                ? sectionRepository.findBySubjectId(subjectId)
                : sectionRepository.findBySubjectIdAndStatus(subjectId, status);
    }

    public SubjectSection get(Long id) {
        return findOrThrow(id);
    }

    @Transactional
    public void deactivate(Long id) {
        SubjectSection section = findOrThrow(id);
        section.setStatus(StatusType.INACTIVE);
        sectionRepository.save(section);
    }

    @Transactional
    public void reactivate(Long id) {
        SubjectSection section = findOrThrow(id);
        section.setStatus(StatusType.ACTIVE);
        sectionRepository.save(section);
    }

    @Transactional
    public SubjectSectionInstructor assignInstructor(Long sectionId, AssignInstructorRequest request) {
        SubjectSection section = findOrThrow(sectionId);
        Instructor instructor = instructorRepository.findById(request.instructorId())
                .orElseThrow(() -> new InstructorNotFoundException(request.instructorId()));

        SubjectSectionInstructor assignment = new SubjectSectionInstructor();
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

    public List<SubjectSectionInstructor> listInstructors(Long sectionId) {
        return sectionInstructorRepository.findBySectionId(sectionId);
    }

    @Transactional
    public void removeInstructor(Long sectionId, Long instructorAssignmentId) {
        SubjectSectionInstructor assignment = sectionInstructorRepository.findById(instructorAssignmentId)
                .filter(a -> a.getSection().getId().equals(sectionId))
                .orElseThrow(() -> new SectionInstructorNotFoundException(instructorAssignmentId));
        sectionInstructorRepository.delete(assignment);
    }

    private SubjectSection findOrThrow(Long id) {
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
