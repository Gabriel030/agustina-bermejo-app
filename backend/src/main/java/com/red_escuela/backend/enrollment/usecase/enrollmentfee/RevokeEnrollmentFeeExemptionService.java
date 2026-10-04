package com.red_escuela.backend.enrollment.usecase.enrollmentfee;

import com.red_escuela.backend.enrollment.entity.EnrollmentFeeExemption;
import com.red_escuela.backend.enrollment.exception.EnrollmentFeeExemptionNotFoundException;
import com.red_escuela.backend.enrollment.repository.EnrollmentFeeExemptionRepository;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.notification.EnrollmentFeeExemptionRevokedEvent;
import com.red_escuela.backend.shared.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@UseCase
public class RevokeEnrollmentFeeExemptionService implements RevokeEnrollmentFeeExemptionUseCase {
    private static final Logger log = LoggerFactory.getLogger(RevokeEnrollmentFeeExemptionService.class);

    private final EnrollmentFeeExemptionRepository exemptionRepository;
    private final CurrentPersonService currentPersonService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RevokeEnrollmentFeeExemptionService(EnrollmentFeeExemptionRepository exemptionRepository, CurrentPersonService currentPersonService,
                                               ApplicationEventPublisher eventPublisher, Clock clock) {
        this.exemptionRepository = exemptionRepository;
        this.currentPersonService = currentPersonService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void revoke(Long exemptionId, String notes) {
        EnrollmentFeeExemption exemption = exemptionRepository.findById(exemptionId)
                .orElseThrow(() -> new EnrollmentFeeExemptionNotFoundException(exemptionId));
        exemption.revoke(currentPersonService.resolveCurrentPerson(), LocalDateTime.now(clock), notes);
        exemptionRepository.save(exemption);
        log.info("Habilitación de matrícula revocada: habilitacion={}, inscripcion={}", exemptionId, exemption.getProgramEnrollment().getId());
        eventPublisher.publishEvent(new EnrollmentFeeExemptionRevokedEvent(exemption));
    }
}
