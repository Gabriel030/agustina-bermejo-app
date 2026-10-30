package com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.evaluation.exception.InvalidSectionEvaluationConfigTransitionException;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import com.red_escuela.backend.evaluation.service.SectionEvaluationConfigFinder;
import com.red_escuela.backend.evaluation.service.SectionFinalCallCreator;
import com.red_escuela.backend.evaluation.service.SectionEvaluationConfigInstructorAccess;
import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigApprovedEvent;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import com.red_escuela.backend.shared.time.ApplicationTime;

@UseCase
public class ApproveSectionEvaluationConfigService implements ApproveSectionEvaluationConfigUseCase {

    private final SectionEvaluationConfigRepository configRepository;
    private final SectionEvaluationConfigFinder configFinder;
    private final SectionEvaluationConfigInstructorAccess instructorAccess;
    private final SectionFinalCallCreator finalCallCreator;
    private final ApplicationEventPublisher eventPublisher;

    public ApproveSectionEvaluationConfigService(SectionEvaluationConfigRepository configRepository,
                                                  SectionEvaluationConfigFinder configFinder,
                                                  SectionEvaluationConfigInstructorAccess instructorAccess,
                                                  SectionFinalCallCreator finalCallCreator,
                                                  ApplicationEventPublisher eventPublisher) {
        this.configRepository = configRepository;
        this.configFinder = configFinder;
        this.instructorAccess = instructorAccess;
        this.finalCallCreator = finalCallCreator;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public SectionEvaluationConfig approve(ApproveSectionEvaluationConfigCommand command) {
        SectionEvaluationConfig config = configFinder.findOrThrow(command.sectionId());
        if (config.getStatus() != SectionEvaluationConfigStatus.PENDING_APPROVAL) {
            throw new InvalidSectionEvaluationConfigTransitionException(config.getStatus(), "aprobar");
        }
        config.setStatus(SectionEvaluationConfigStatus.APPROVED);
        config.setReviewedBy(command.actor());
        config.setReviewedAt(ApplicationTime.now());
        SectionEvaluationConfig saved = configRepository.save(config);

        finalCallCreator.createFinalCalls(config);

        Long leadInstructorPersonId = instructorAccess.leadInstructorPersonId(command.sectionId());
        eventPublisher.publishEvent(SectionEvaluationConfigApprovedEvent.approvedFor(saved, leadInstructorPersonId));
        return saved;
    }
}
