package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.events.SectionEvaluationConfigProposedEvent;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SectionEvaluationConfigSubmissionTransaction {

    private final SectionEvaluationConfigRepository configRepository;
    private final SectionEvaluationConfigFinder configFinder;
    private final SectionEvaluationConfigInstructorAccessChecker instructorAccess;
    private final ApplicationEventPublisher eventPublisher;

    public SectionEvaluationConfigSubmissionTransaction(SectionEvaluationConfigRepository configRepository,
                                                        SectionEvaluationConfigFinder configFinder,
                                                        SectionEvaluationConfigInstructorAccessChecker instructorAccess,
                                                        ApplicationEventPublisher eventPublisher) {
        this.configRepository = configRepository;
        this.configFinder = configFinder;
        this.instructorAccess = instructorAccess;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public SectionEvaluationConfig submit(Long sectionId, Long instructorId) {
        instructorAccess.requireLeadInstructor(sectionId, instructorId);
        SectionEvaluationConfig config = configFinder.findOrThrow(sectionId);
        config.submitForApproval(ApplicationTime.now());
        SectionEvaluationConfig saved = configRepository.save(config);
        eventPublisher.publishEvent(SectionEvaluationConfigProposedEvent.proposedFor(saved));
        return saved;
    }
}
