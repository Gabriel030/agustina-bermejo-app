package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentApplicationNotFoundException;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentApplicationRepository;
import org.springframework.stereotype.Service;

@Service
public class ProgramEnrollmentApplicationFinder {

    private final ProgramEnrollmentApplicationRepository applicationRepository;

    public ProgramEnrollmentApplicationFinder(ProgramEnrollmentApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    public ProgramEnrollmentApplication findOrThrow(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new ProgramEnrollmentApplicationNotFoundException(id));
    }
}
