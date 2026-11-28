package com.red_escuela.backend.graduation.specialist;

import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
        List<GraduationRequest> open = graduationRequestRepository
                .findByProgramEnrollmentIdAndStatusNot(programEnrollmentId, GraduationRequestStatus.REJECTED);
        if (!open.isEmpty()) {
            if (graduationEligibilityChecker.isEligibleToGraduate(programEnrollmentId)) {
                open.stream().filter(request -> request.getOutdatedAt() != null).forEach(request -> {
                    request.clearOutdated();
                    graduationRequestRepository.save(request);
                });
            }
            return;
        }
        if (!graduationEligibilityChecker.isEligibleToGraduate(programEnrollmentId)) {
            return;
        }
        graduationRequestRegistrar.registerRequest(programEnrollmentId);
    }
}
