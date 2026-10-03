package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.dto.CreateSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateSubjectRequest;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectStatus;
import com.red_escuela.backend.academicstructure.exception.SubjectAlreadyExistsException;
import com.red_escuela.backend.academicstructure.exception.SubjectNotFoundException;
import com.red_escuela.backend.academicstructure.repository.SubjectRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SubjectService {
    private final SubjectRepository subjectRepository;

    public SubjectService(SubjectRepository subjectRepository) {
        this.subjectRepository = subjectRepository;
    }

    @Transactional
    public Subject create(CreateSubjectRequest request) {
        Subject subject = Subject.builder()
                .name(request.name())
                .code(request.code())
                .description(request.description())
                .weeklyHours(request.weeklyHours())
                .weeklyHoursLab(request.weeklyHoursLab())
                .gradeMode(request.gradeMode())
                .build();
        subject.setMaxFinalExamAttempts(request.maxFinalExamAttempts());
        try {
            return subjectRepository.saveAndFlush(subject);
        } catch (DataIntegrityViolationException e) {
            throw new SubjectAlreadyExistsException(request.name());
        }
    }

    @Transactional
    public Subject update(Long id, UpdateSubjectRequest request) {
        Subject subject = findOrThrow(id);
        subject.requireEditable();
        Optional.ofNullable(request.code()).ifPresent(subject::changeCode);
        Optional.ofNullable(request.name()).ifPresent(subject::setName);
        Optional.ofNullable(request.description()).ifPresent(subject::setDescription);
        Optional.ofNullable(request.weeklyHours()).ifPresent(subject::setWeeklyHours);
        Optional.ofNullable(request.weeklyHoursLab()).ifPresent(subject::setWeeklyHoursLab);
        Optional.ofNullable(request.gradeMode()).ifPresent(subject::setGradeMode);
        Optional.ofNullable(request.maxFinalExamAttempts()).ifPresent(subject::setMaxFinalExamAttempts);
        try {
            return subjectRepository.saveAndFlush(subject);
        } catch (DataIntegrityViolationException e) {
            throw new SubjectAlreadyExistsException(subject.getName());
        }
    }

    @Transactional
    public Subject submitForApproval(Long id) {
        Subject subject = findOrThrow(id);
        subject.submitForApproval();
        return subjectRepository.save(subject);
    }

    @Transactional
    public Subject approve(Long id) {
        Subject subject = findOrThrow(id);
        subject.approve();
        return subjectRepository.save(subject);
    }

    @Transactional
    public Subject reject(Long id, String reason) {
        Subject subject = findOrThrow(id);
        subject.reject(reason.trim());
        return subjectRepository.save(subject);
    }

    public List<Subject> list(SubjectStatus status) {
        return status == null ? subjectRepository.findAll() : subjectRepository.findByStatus(status);
    }

    public Subject get(Long id) {
        return findOrThrow(id);
    }

    @Transactional
    public void deactivate(Long id) {
        Subject subject = findOrThrow(id);
        subject.deactivate();
        subjectRepository.save(subject);
    }

    @Transactional
    public void reactivate(Long id) {
        Subject subject = findOrThrow(id);
        subject.reactivate();
        subjectRepository.save(subject);
    }

    private Subject findOrThrow(Long id) {
        return subjectRepository.findById(id).orElseThrow(() -> new SubjectNotFoundException(id));
    }
}
