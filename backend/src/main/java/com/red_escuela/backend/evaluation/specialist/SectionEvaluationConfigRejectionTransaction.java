package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigRejectedEvent;
import com.red_escuela.backend.evaluation.exception.InvalidSectionEvaluationConfigTransitionException;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SectionEvaluationConfigRejectionTransaction {

    private final SectionEvaluationConfigRepository configRepository;
    private final SectionEvaluationConfigFinder configFinder;
    private final SectionEvaluationConfigInstructorAccessChecker instructorAccess;
    private final SectionEvaluationConfigReviewer configReviewer;
    private final ApplicationEventPublisher eventPublisher;

    public SectionEvaluationConfigRejectionTransaction(SectionEvaluationConfigRepository configRepository,
                                                       SectionEvaluationConfigFinder configFinder,
                                                       SectionEvaluationConfigInstructorAccessChecker instructorAccess,
                                                       SectionEvaluationConfigReviewer configReviewer,
                                                       ApplicationEventPublisher eventPublisher) {
        this.configRepository = configRepository;
        this.configFinder = configFinder;
        this.instructorAccess = instructorAccess;
        this.configReviewer = configReviewer;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public SectionEvaluationConfig reject(Long sectionId, String reason) {
        SectionEvaluationConfig config = configFinder.findOrThrow(sectionId);
        if (config.getStatus() != SectionEvaluationConfigStatus.PENDING_APPROVAL) {
            throw new InvalidSectionEvaluationConfigTransitionException(config.getStatus(), "rechazar");
        }
        configReviewer.markReviewedByCurrentActor(config, SectionEvaluationConfigStatus.REJECTED);
        config.setRejectionReason(reason);
        SectionEvaluationConfig saved = configRepository.save(config);

        Long leadInstructorPersonId = instructorAccess.leadInstructorPersonId(sectionId);
        eventPublisher.publishEvent(SectionEvaluationConfigRejectedEvent.rejectedFor(saved, leadInstructorPersonId));
        return saved;
    }
}
