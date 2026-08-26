package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateTermRequest;
import com.red_escuela.backend.dto.UpdateTermRequest;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.entity.TermEnrollmentOverride;
import com.red_escuela.backend.exception.TermNotFoundException;
import com.red_escuela.backend.repository.TermRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TermService {

    private final TermRepository termRepository;

    public TermService(TermRepository termRepository) {
        this.termRepository = termRepository;
    }

    @Transactional
    public Term crearPeriodo(CreateTermRequest request) {
        Term term = new Term();
        term.setYear(request.year());
        term.setDescription(request.description());
        term.setType(request.type());
        term.setStartDate(request.startDate());
        term.setEndDate(request.endDate());
        term.setEnrollmentStart(request.enrollmentStart());
        term.setEnrollmentEnd(request.enrollmentEnd());
        term.setStatus(StatusType.ACTIVE);
        return termRepository.save(term);
    }

    @Transactional
    public Term editarPeriodo(Long id, UpdateTermRequest request) {
        Term term = findOrThrow(id);
        if (request.description() != null) {
            term.setDescription(request.description());
        }
        if (request.startDate() != null) {
            term.setStartDate(request.startDate());
        }
        if (request.endDate() != null) {
            term.setEndDate(request.endDate());
        }
        if (request.enrollmentStart() != null) {
            term.setEnrollmentStart(request.enrollmentStart());
        }
        if (request.enrollmentEnd() != null) {
            term.setEnrollmentEnd(request.enrollmentEnd());
        }
        return termRepository.save(term);
    }

    public List<Term> listar(StatusType status) {
        return status == null ? termRepository.findAll() : termRepository.findByStatus(status);
    }

    public Term verDetalle(Long id) {
        return findOrThrow(id);
    }

    @Transactional
    public void desactivar(Long id) {
        Term term = findOrThrow(id);
        term.setStatus(StatusType.INACTIVE);
        termRepository.save(term);
    }

    @Transactional
    public void reactivar(Long id) {
        Term term = findOrThrow(id);
        term.setStatus(StatusType.ACTIVE);
        termRepository.save(term);
    }

    // Decisión 5 (Spec Procesos administrativos): override manual con prioridad sobre la
    // validación por fecha ya existente, mientras esté activo.
    @Transactional
    public void abrirInscripcion(Long id) {
        Term term = findOrThrow(id);
        term.setEnrollmentOverride(TermEnrollmentOverride.OPEN);
        termRepository.save(term);
    }

    @Transactional
    public void cerrarInscripcion(Long id) {
        Term term = findOrThrow(id);
        term.setEnrollmentOverride(TermEnrollmentOverride.CLOSED);
        termRepository.save(term);
    }

    @Transactional
    public void quitarOverride(Long id) {
        Term term = findOrThrow(id);
        term.setEnrollmentOverride(null);
        termRepository.save(term);
    }

    private Term findOrThrow(Long id) {
        return termRepository.findById(id).orElseThrow(() -> new TermNotFoundException(id));
    }
}
