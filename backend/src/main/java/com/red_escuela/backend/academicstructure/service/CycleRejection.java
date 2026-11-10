package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.events.AcademicOfferingCycleRejectedEvent;
import com.red_escuela.backend.academicstructure.exception.CycleDecisionIdempotencyKeyConflictException;
import com.red_escuela.backend.academicstructure.exception.CycleNotFoundException;
import com.red_escuela.backend.academicstructure.repository.AcademicOfferingCycleRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CycleRejection {

    private final AcademicOfferingCycleRepository cycleRepository;
    private final AcademicOfferingCycleReviewer cycleReviewer;
    private final ApplicationEventPublisher eventPublisher;

    public CycleRejection(AcademicOfferingCycleRepository cycleRepository, AcademicOfferingCycleReviewer cycleReviewer,
                              ApplicationEventPublisher eventPublisher) {
        this.cycleRepository = cycleRepository;
        this.cycleReviewer = cycleReviewer;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void reject(Long cycleId, String reason, String idempotencyKey) {
        var key = CycleDecisionIdempotencyKeys.normalize(idempotencyKey);
        var cycle = cycleRepository.findByIdForUpdate(cycleId)
                .orElseThrow(() -> new CycleNotFoundException(cycleId));
        if (cycle.wasDecidedWithKey(key)) {
            if (cycle.getStatus() == AcademicOfferingCycleStatus.REJECTED) {
                return;
            }
            throw new CycleDecisionIdempotencyKeyConflictException(cycleId);
        }
        cycleReviewer.reject(cycle, reason, key);
        cycleRepository.save(cycle);
        eventPublisher.publishEvent(AcademicOfferingCycleRejectedEvent.rejectedFor(cycle));
    }
}
