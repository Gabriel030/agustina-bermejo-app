package com.red_escuela.backend.graduation.specialist;

import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class GraduationDecisionRecorder {

    private final CurrentActorFinder currentActorFinder;
    private final GraduationRequestRepository graduationRequestRepository;
    private final GraduationRequestApprover graduationRequestApprover;
    private final Clock clock;

    public GraduationDecisionRecorder(CurrentActorFinder currentActorFinder,
                                      GraduationRequestRepository graduationRequestRepository,
                                      GraduationRequestApprover graduationRequestApprover,
                                      Clock clock) {
        this.currentActorFinder = currentActorFinder;
        this.graduationRequestRepository = graduationRequestRepository;
        this.graduationRequestApprover = graduationRequestApprover;
        this.clock = clock;
    }

    public GraduationRequest recordVerification(GraduationRequest request) {
        request.setStatus(GraduationRequestStatus.ADMIN_VERIFIED);
        request.setVerifiedBy(currentActorFinder.currentPerson());
        request.setVerifiedAt(LocalDateTime.now(clock));
        return graduationRequestRepository.save(request);
    }

    public GraduationRequest recordRejection(GraduationRequest request, String reason) {
        request.setStatus(GraduationRequestStatus.REJECTED);
        request.setDecidedBy(currentActorFinder.currentPerson());
        request.setDecidedAt(LocalDateTime.now(clock));
        request.setRejectionReason(reason);
        return graduationRequestRepository.save(request);
    }

    public void recordApproval(GraduationRequest request) {
        graduationRequestApprover.recordApproval(request, currentActorFinder.currentPerson(), LocalDateTime.now(clock));
    }
}
