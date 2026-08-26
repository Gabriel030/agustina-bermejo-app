package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.AssignInstructorRequest;
import com.red_escuela.backend.dto.CreateSectionRequest;
import com.red_escuela.backend.dto.UpdateSectionRequest;
import com.red_escuela.backend.entity.EvaluationScheme;
import com.red_escuela.backend.entity.Instructor;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Subject;
import com.red_escuela.backend.entity.SubjectSection;
import com.red_escuela.backend.entity.SubjectSectionInstructor;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.exception.EvaluationSchemeNotFoundException;
import com.red_escuela.backend.exception.InstructorAlreadyAssignedException;
import com.red_escuela.backend.exception.InstructorNotFoundException;
import com.red_escuela.backend.exception.SectionInstructorNotFoundException;
import com.red_escuela.backend.exception.SectionNotFoundException;
import com.red_escuela.backend.exception.SubjectNotFoundException;
import com.red_escuela.backend.exception.TermNotFoundException;
import com.red_escuela.backend.repository.InstructorRepository;
import com.red_escuela.backend.repository.SubjectRepository;
import com.red_escuela.backend.repository.SubjectSectionInstructorRepository;
import com.red_escuela.backend.repository.SubjectSectionRepository;
import com.red_escuela.backend.repository.TermRepository;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SubjectSectionService {

    private final SubjectSectionRepository sectionRepository;
    private final SubjectRepository subjectRepository;
    private final TermRepository termRepository;
    private final SubjectSectionInstructorRepository sectionInstructorRepository;
    private final InstructorRepository instructorRepository;
    private final EntityManager entityManager;

    public SubjectSectionService(
            SubjectSectionRepository sectionRepository,
            SubjectRepository subjectRepository,
            TermRepository termRepository,
            SubjectSectionInstructorRepository sectionInstructorRepository,
            InstructorRepository instructorRepository,
            EntityManager entityManager) {
        this.sectionRepository = sectionRepository;
        this.subjectRepository = subjectRepository;
        this.termRepository = termRepository;
        this.sectionInstructorRepository = sectionInstructorRepository;
        this.instructorRepository = instructorRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public SubjectSection crearComision(Long subjectId, CreateSectionRequest request) {
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
        return sectionRepository.save(section);
    }

    @Transactional
    public SubjectSection editarComision(Long id, UpdateSectionRequest request) {
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

    public List<SubjectSection> listarPorMateria(Long subjectId, StatusType status) {
        return status == null
                ? sectionRepository.findBySubjectId(subjectId)
                : sectionRepository.findBySubjectIdAndStatus(subjectId, status);
    }

    public SubjectSection verDetalle(Long id) {
        return findOrThrow(id);
    }

    @Transactional
    public void desactivar(Long id) {
        SubjectSection section = findOrThrow(id);
        section.setStatus(StatusType.INACTIVE);
        sectionRepository.save(section);
    }

    @Transactional
    public void reactivar(Long id) {
        SubjectSection section = findOrThrow(id);
        section.setStatus(StatusType.ACTIVE);
        sectionRepository.save(section);
    }

    @Transactional
    public SubjectSectionInstructor asignarDocente(Long sectionId, AssignInstructorRequest request) {
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

    public List<SubjectSectionInstructor> listarDocentes(Long sectionId) {
        return sectionInstructorRepository.findBySectionId(sectionId);
    }

    @Transactional
    public void quitarDocente(Long sectionId, Long instructorAssignmentId) {
        // Antes no validaba nada: ni que la asignación existiera (deleteById no tira
        // excepción si falta), ni que perteneciera a ESTA comisión — se podía borrar la
        // asignación de otra sección con solo conocer su id, ignorando el sectionId de la URL.
        SubjectSectionInstructor assignment = sectionInstructorRepository.findById(instructorAssignmentId)
                .filter(a -> a.getSection().getId().equals(sectionId))
                .orElseThrow(() -> new SectionInstructorNotFoundException(instructorAssignmentId));
        sectionInstructorRepository.delete(assignment);
    }

    private SubjectSection findOrThrow(Long id) {
        return sectionRepository.findById(id).orElseThrow(() -> new SectionNotFoundException(id));
    }

    // entityManager.getReference() no dispara la consulta hasta el flush, así que un id
    // inexistente terminaba en una DataIntegrityViolationException sin capturar (500) en
    // vez de un 404 claro. find() sí resuelve la fila ahora, permitiendo validar antes.
    private EvaluationScheme resolveEvaluationScheme(Long id) {
        EvaluationScheme scheme = entityManager.find(EvaluationScheme.class, id);
        if (scheme == null) {
            throw new EvaluationSchemeNotFoundException(id);
        }
        return scheme;
    }
}
