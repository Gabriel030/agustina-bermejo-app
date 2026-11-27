package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigApprovedEvent;
import com.red_escuela.backend.evaluation.exception.InvalidSectionEvaluationConfigTransitionException;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SectionEvaluationConfigApprovalTransaction {

    private final SectionEvaluationConfigRepository configRepository;
    private final SectionEvaluationConfigFinder configFinder;
    private final SectionEvaluationConfigInstructorAccessChecker instructorAccess;
    private final SectionFinalCallCreator finalCallCreator;
    private final SectionEvaluationConfigReviewer configReviewer;
    private final ApplicationEventPublisher eventPublisher;
    private final SectionEvaluationConfigCompletenessValidator completenessValidator;

    public SectionEvaluationConfigApprovalTransaction(SectionEvaluationConfigRepository configRepository,
                                                      SectionEvaluationConfigFinder configFinder,
                                                      SectionEvaluationConfigInstructorAccessChecker instructorAccess,
                                                      SectionFinalCallCreator finalCallCreator,
                                                      SectionEvaluationConfigReviewer configReviewer,
                                                      ApplicationEventPublisher eventPublisher,
                                                      SectionEvaluationConfigCompletenessValidator completenessValidator) {
        this.configRepository = configRepository;
        this.configFinder = configFinder;
        this.instructorAccess = instructorAccess;
        this.finalCallCreator = finalCallCreator;
        this.configReviewer = configReviewer;
        this.eventPublisher = eventPublisher;
        this.completenessValidator = completenessValidator;
    }

    @Transactional
    public SectionEvaluationConfig approve(Long sectionId) {
        SectionEvaluationConfig config = configFinder.findOrThrow(sectionId);
        if (config.getStatus() != SectionEvaluationConfigStatus.PENDING_APPROVAL) {
            throw new InvalidSectionEvaluationConfigTransitionException(config.getStatus(), "aprobar");
        }
        completenessValidator.requireComplete(config);
        configReviewer.markReviewedByCurrentActor(config, SectionEvaluationConfigStatus.APPROVED);
        SectionEvaluationConfig saved = configRepository.save(config);

        finalCallCreator.createFinalCalls(config);

        Long leadInstructorPersonId = instructorAccess.leadInstructorPersonId(sectionId);
        eventPublisher.publishEvent(SectionEvaluationConfigApprovedEvent.approvedFor(saved, leadInstructorPersonId));
        return saved;
    }
}
