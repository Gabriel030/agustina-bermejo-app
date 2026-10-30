package com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.evaluation.exception.InvalidSectionEvaluationConfigTransitionException;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import com.red_escuela.backend.evaluation.service.SectionEvaluationConfigFinder;
import com.red_escuela.backend.evaluation.service.SectionEvaluationConfigInstructorAccess;
import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigRejectedEvent;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;


@UseCase
public class RejectSectionEvaluationConfigService implements RejectSectionEvaluationConfigUseCase {

    private final SectionEvaluationConfigRepository configRepository;
    private final SectionEvaluationConfigFinder configFinder;
    private final SectionEvaluationConfigInstructorAccess instructorAccess;
    private final ApplicationEventPublisher eventPublisher;

    public RejectSectionEvaluationConfigService(SectionEvaluationConfigRepository configRepository,
                                                 SectionEvaluationConfigFinder configFinder,
                                                 SectionEvaluationConfigInstructorAccess instructorAccess,
                                                 ApplicationEventPublisher eventPublisher) {
        this.configRepository = configRepository;
        this.configFinder = configFinder;
        this.instructorAccess = instructorAccess;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public SectionEvaluationConfig reject(RejectSectionEvaluationConfigCommand command) {
        SectionEvaluationConfig config = configFinder.findOrThrow(command.sectionId());
        if (config.getStatus() != SectionEvaluationConfigStatus.PENDING_APPROVAL) {
            throw new InvalidSectionEvaluationConfigTransitionException(config.getStatus(), "rechazar");
        }
        config.setStatus(SectionEvaluationConfigStatus.REJECTED);
        config.setRejectionReason(command.request().reason());
        config.setReviewedBy(command.actor());
        config.setReviewedAt(ApplicationTime.now());
        SectionEvaluationConfig saved = configRepository.save(config);

        Long leadInstructorPersonId = instructorAccess.leadInstructorPersonId(command.sectionId());
        eventPublisher.publishEvent(SectionEvaluationConfigRejectedEvent.rejectedFor(saved, leadInstructorPersonId));
        return saved;
    }
}
