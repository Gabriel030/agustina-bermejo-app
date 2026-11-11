package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ProgramEnrollmentGraduationRecorder {

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final EntityManager entityManager;

    public ProgramEnrollmentGraduationRecorder(ProgramEnrollmentRepository programEnrollmentRepository, EntityManager entityManager) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public void lockForGraduation(ProgramEnrollment enrollment) {
        programEnrollmentRepository.findByIdForUpdate(enrollment.getId()).ifPresent(entityManager::refresh);
    }

    @Transactional
    public void recordGraduation(ProgramEnrollment graduatedEnrollment) {
        programEnrollmentRepository.save(graduatedEnrollment);
    }
}
