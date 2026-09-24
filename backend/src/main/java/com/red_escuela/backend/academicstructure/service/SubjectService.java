package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.dto.CreateSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateSubjectRequest;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.exception.SubjectAlreadyExistsException;
import com.red_escuela.backend.academicstructure.exception.SubjectNotFoundException;
import com.red_escuela.backend.academicstructure.repository.SubjectRepository;
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
    public Subject create(CreateSubjectRequest request) {
        Subject subject = new Subject();
        subject.setName(request.name());
        subject.setCode(request.code());
        subject.setDescription(request.description());
        subject.setWeeklyHours(request.weeklyHours());
        subject.setWeeklyHoursLab(request.weeklyHoursLab());
        subject.setGradeMode(request.gradeMode());
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
        if (request.name() != null) {
            subject.setName(request.name());
        }
        if (request.description() != null) {
            subject.setDescription(request.description());
        }
        if (request.weeklyHours() != null) {
            subject.setWeeklyHours(request.weeklyHours());
        }
        if (request.weeklyHoursLab() != null) {
            subject.setWeeklyHoursLab(request.weeklyHoursLab());
        }
        if (request.gradeMode() != null) {
            subject.setGradeMode(request.gradeMode());
        }
        if (request.maxFinalExamAttempts() != null) {
            subject.setMaxFinalExamAttempts(request.maxFinalExamAttempts());
        }
        return subjectRepository.save(subject);
    }

    public List<Subject> list(StatusType status) {
        return status == null ? subjectRepository.findAll() : subjectRepository.findByStatus(status);
    }

    public Subject get(Long id) {
        return findOrThrow(id);
    }

    @Transactional
    public void deactivate(Long id) {
        Subject subject = findOrThrow(id);
        subject.setStatus(StatusType.INACTIVE);
        subjectRepository.save(subject);
    }

    @Transactional
    public void reactivate(Long id) {
        Subject subject = findOrThrow(id);
        subject.setStatus(StatusType.ACTIVE);
        subjectRepository.save(subject);
    }

    private Subject findOrThrow(Long id) {
        return subjectRepository.findById(id).orElseThrow(() -> new SubjectNotFoundException(id));
    }
}
