package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigFinder;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigInstructorAccessChecker;
import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigProposedEvent;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;


@UseCase
public class SubmitSectionEvaluationConfigForApprovalService implements SubmitSectionEvaluationConfigForApprovalUseCase {

    private final SectionEvaluationConfigRepository configRepository;
    private final SectionEvaluationConfigFinder configFinder;
    private final SectionEvaluationConfigInstructorAccessChecker instructorAccess;
    private final ApplicationEventPublisher eventPublisher;

    public SubmitSectionEvaluationConfigForApprovalService(SectionEvaluationConfigRepository configRepository,
                                                             SectionEvaluationConfigFinder configFinder,
                                                             SectionEvaluationConfigInstructorAccessChecker instructorAccess,
                                                             ApplicationEventPublisher eventPublisher) {
        this.configRepository = configRepository;
        this.configFinder = configFinder;
        this.instructorAccess = instructorAccess;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public SectionEvaluationConfig submit(SubmitSectionEvaluationConfigForApprovalCommand command) {
        instructorAccess.requireLeadInstructor(command.sectionId(), command.instructorId());
        SectionEvaluationConfig config = configFinder.findOrThrow(command.sectionId());
        config.submitForApproval(ApplicationTime.now());
        SectionEvaluationConfig saved = configRepository.save(config);
        eventPublisher.publishEvent(SectionEvaluationConfigProposedEvent.proposedFor(saved));
        return saved;
    }
}
