package com.red_escuela.backend.enrollment.service;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import com.red_escuela.backend.enrollment.entity.EnrollmentFeeExemption;
import com.red_escuela.backend.enrollment.exception.EnrollmentFeeExemptionAlreadyRevokedException;
import com.red_escuela.backend.enrollment.exception.EnrollmentFeeExemptionNotFoundException;
import com.red_escuela.backend.enrollment.repository.EnrollmentFeeExemptionRepository;
import com.red_escuela.backend.identity.specialist.CurrentPersonService;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class RevokeEnrollmentFeeExemptionTransaction {
    private static final Logger log = LoggerFactory.getLogger(RevokeEnrollmentFeeExemptionTransaction.class);

    private final EnrollmentFeeExemptionRepository exemptionRepository;
    private final CurrentPersonService currentPersonService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RevokeEnrollmentFeeExemptionTransaction(EnrollmentFeeExemptionRepository exemptionRepository, CurrentPersonService currentPersonService,
                                               ApplicationEventPublisher eventPublisher, Clock clock) {
        this.exemptionRepository = exemptionRepository;
        this.currentPersonService = currentPersonService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void revoke(Long exemptionId, String notes) {
        EnrollmentFeeExemption exemption = exemptionRepository.findById(exemptionId)
                .orElseThrow(() -> new EnrollmentFeeExemptionNotFoundException(exemptionId));
        exemption.revoke(currentPersonService.resolveCurrentPerson(), LocalDateTime.now(clock), notes);
        try {
            exemptionRepository.saveAndFlush(exemption);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new EnrollmentFeeExemptionAlreadyRevokedException(exemptionId);
        }
        log.info("Habilitación de matrícula revocada: habilitacion={}, inscripcion={}", exemptionId, exemption.getProgramEnrollment().getId());
        eventPublisher.publishEvent(EnrollmentNotificationEvents.exemptionRevocationOf(exemption));
    }
}
