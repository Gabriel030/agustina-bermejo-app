package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ProgramEnrollmentGraduationRecorder {

    private final ProgramEnrollmentRepository programEnrollmentRepository;

    public ProgramEnrollmentGraduationRecorder(ProgramEnrollmentRepository programEnrollmentRepository) {
        this.programEnrollmentRepository = programEnrollmentRepository;
    }

    @Transactional
    public void recordGraduation(ProgramEnrollment graduatedEnrollment) {
        programEnrollmentRepository.save(graduatedEnrollment);
    }
}
