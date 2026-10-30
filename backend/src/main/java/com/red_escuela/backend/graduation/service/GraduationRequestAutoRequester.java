package com.red_escuela.backend.graduation.service;

import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GraduationRequestAutoRequester {

    private final GraduationEligibilityChecker graduationEligibilityChecker;
    private final GraduationRequestRepository graduationRequestRepository;
    private final GraduationRequestRegistrar graduationRequestRegistrar;

    public GraduationRequestAutoRequester(GraduationEligibilityChecker graduationEligibilityChecker,
                                           GraduationRequestRepository graduationRequestRepository,
                                           GraduationRequestRegistrar graduationRequestRegistrar) {
        this.graduationEligibilityChecker = graduationEligibilityChecker;
        this.graduationRequestRepository = graduationRequestRepository;
        this.graduationRequestRegistrar = graduationRequestRegistrar;
    }

    @Transactional
    public void createIfEligible(Long programEnrollmentId) {
        if (graduationRequestRepository.existsByProgramEnrollmentIdAndStatusNot(programEnrollmentId, GraduationRequestStatus.REJECTED)) {
            return;
        }
        if (!graduationEligibilityChecker.isEligibleToGraduate(programEnrollmentId)) {
            return;
        }
        graduationRequestRegistrar.registerRequest(programEnrollmentId);
    }
}
