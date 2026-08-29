package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateSubjectRequest;
import com.red_escuela.backend.dto.UpdateSubjectRequest;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Subject;
import com.red_escuela.backend.exception.SubjectAlreadyExistsException;
import com.red_escuela.backend.exception.SubjectNotFoundException;
import com.red_escuela.backend.repository.SubjectRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SubjectService {

    private final SubjectRepository subjectRepository;

    public SubjectService(SubjectRepository subjectRepository) {
        this.subjectRepository = subjectRepository;
    }

    @Transactional
    public Subject crearMateria(CreateSubjectRequest request) {
        Subject subject = new Subject();
        subject.setName(request.name());
        subject.setDescription(request.description());
        subject.setCode(generarProximoCodigo());
        try {
            return subjectRepository.saveAndFlush(subject);
        } catch (DataIntegrityViolationException e) {
            throw new SubjectAlreadyExistsException(request.name());
        }
    }

    // Código de 4 dígitos, secuencial a partir del último asignado (backfill de la
    // migración arrancó en 1001) — quien da de alta la materia no lo elige, ver
    // comentario en Subject.java.
    private String generarProximoCodigo() {
        int ultimo = subjectRepository.findTopByOrderByCodeDesc().map(s -> Integer.parseInt(s.getCode())).orElse(1000);
        return String.format("%04d", ultimo + 1);
    }

    @Transactional
    public Subject editarMateria(Long id, UpdateSubjectRequest request) {
        Subject subject = findOrThrow(id);
        if (request.name() != null) {
            subject.setName(request.name());
        }
        if (request.description() != null) {
            subject.setDescription(request.description());
        }
        return subjectRepository.save(subject);
    }

    public List<Subject> listar(StatusType status) {
        return status == null ? subjectRepository.findAll() : subjectRepository.findByStatus(status);
    }

    public Subject verDetalle(Long id) {
        return findOrThrow(id);
    }

    @Transactional
    public void desactivar(Long id) {
        Subject subject = findOrThrow(id);
        subject.setStatus(StatusType.INACTIVE);
        subjectRepository.save(subject);
    }

    @Transactional
    public void reactivar(Long id) {
        Subject subject = findOrThrow(id);
        subject.setStatus(StatusType.ACTIVE);
        subjectRepository.save(subject);
    }

    private Subject findOrThrow(Long id) {
        return subjectRepository.findById(id).orElseThrow(() -> new SubjectNotFoundException(id));
    }
}
