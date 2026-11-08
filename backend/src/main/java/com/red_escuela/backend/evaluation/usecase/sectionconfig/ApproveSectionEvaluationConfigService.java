package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.evaluation.exception.InvalidSectionEvaluationConfigTransitionException;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigFinder;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigReviewer;
import com.red_escuela.backend.evaluation.specialist.SectionFinalCallCreator;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigInstructorAccessChecker;
import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigApprovedEvent;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class ApproveSectionEvaluationConfigService implements ApproveSectionEvaluationConfigUseCase {

    private final SectionEvaluationConfigRepository configRepository;
    private final SectionEvaluationConfigFinder configFinder;
    private final SectionEvaluationConfigInstructorAccessChecker instructorAccess;
    private final SectionFinalCallCreator finalCallCreator;
    private final SectionEvaluationConfigReviewer configReviewer;
    private final ApplicationEventPublisher eventPublisher;

    public ApproveSectionEvaluationConfigService(SectionEvaluationConfigRepository configRepository,
                                                  SectionEvaluationConfigFinder configFinder,
                                                  SectionEvaluationConfigInstructorAccessChecker instructorAccess,
                                                  SectionFinalCallCreator finalCallCreator,
                                                  SectionEvaluationConfigReviewer configReviewer,
                                                  ApplicationEventPublisher eventPublisher) {
        this.configRepository = configRepository;
        this.configFinder = configFinder;
        this.instructorAccess = instructorAccess;
        this.finalCallCreator = finalCallCreator;
        this.configReviewer = configReviewer;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public SectionEvaluationConfig approve(ApproveSectionEvaluationConfigCommand command) {
        SectionEvaluationConfig config = configFinder.findOrThrow(command.sectionId());
        if (config.getStatus() != SectionEvaluationConfigStatus.PENDING_APPROVAL) {
            throw new InvalidSectionEvaluationConfigTransitionException(config.getStatus(), "aprobar");
        }
        configReviewer.markReviewedByCurrentActor(config, SectionEvaluationConfigStatus.APPROVED);
        SectionEvaluationConfig saved = configRepository.save(config);

        finalCallCreator.createFinalCalls(config);

        Long leadInstructorPersonId = instructorAccess.leadInstructorPersonId(command.sectionId());
        eventPublisher.publishEvent(SectionEvaluationConfigApprovedEvent.approvedFor(saved, leadInstructorPersonId));
        return saved;
    }
}
