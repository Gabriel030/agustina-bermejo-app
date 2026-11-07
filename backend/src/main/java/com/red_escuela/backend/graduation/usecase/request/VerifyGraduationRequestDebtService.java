package com.red_escuela.backend.graduation.usecase.request;

import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.exception.InvalidGraduationRequestStateException;
import com.red_escuela.backend.graduation.events.GraduationRequestVerifiedEvent;
import com.red_escuela.backend.graduation.specialist.GraduationDebtChecker;
import com.red_escuela.backend.graduation.specialist.GraduationDecisionRecorder;
import com.red_escuela.backend.graduation.specialist.GraduationRequestFinder;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class VerifyGraduationRequestDebtService implements VerifyGraduationRequestDebtUseCase {

    private final GraduationRequestFinder graduationRequestFinder;
    private final GraduationDebtChecker graduationDebtChecker;
    private final GraduationDecisionRecorder graduationDecisionRecorder;
    private final ApplicationEventPublisher eventPublisher;

    public VerifyGraduationRequestDebtService(GraduationRequestFinder graduationRequestFinder,
                                               GraduationDebtChecker graduationDebtChecker,
                                               GraduationDecisionRecorder graduationDecisionRecorder,
                                               ApplicationEventPublisher eventPublisher) {
        this.graduationRequestFinder = graduationRequestFinder;
        this.graduationDebtChecker = graduationDebtChecker;
        this.graduationDecisionRecorder = graduationDecisionRecorder;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public GraduationRequest verify(Long graduationRequestId) {
        GraduationRequest request = graduationRequestFinder.findOrThrow(graduationRequestId);
        if (request.getStatus() != GraduationRequestStatus.REQUESTED) {
            throw new InvalidGraduationRequestStateException(graduationRequestId);
        }

        Long studentId = request.getProgramEnrollment().getStudent().getId();
        graduationDebtChecker.requireNoDebtNotifying(request, studentId);

        GraduationRequest saved = graduationDecisionRecorder.recordVerification(request);
        eventPublisher.publishEvent(GraduationRequestVerifiedEvent.verifiedFor(saved));
        return saved;
    }
}
