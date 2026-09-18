package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.ScholarshipStatus;
import com.red_escuela.backend.billing.repository.ScholarshipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScholarshipService {
    private final ScholarshipRepository scholarshipRepository;

    public ScholarshipService(ScholarshipRepository scholarshipRepository) {
        this.scholarshipRepository = scholarshipRepository;
    }

    @Transactional
    public void cancelActiveForCourse(Long courseEnrollmentId, String reason) {
        scholarshipRepository.findByCourseEnrollmentIdAndStatus(courseEnrollmentId, ScholarshipStatus.ACTIVE)
                .ifPresent(scholarship -> {
                    scholarship.cancelRemaining(reason);
                    scholarshipRepository.save(scholarship);
                });
    }

    @Transactional
    public void cancelActiveForProgram(Long programEnrollmentId, String reason) {
        scholarshipRepository.findByProgramEnrollmentIdAndStatus(programEnrollmentId, ScholarshipStatus.ACTIVE)
                .ifPresent(scholarship -> {
                    scholarship.cancelRemaining(reason);
                    scholarshipRepository.save(scholarship);
                });
    }
}
