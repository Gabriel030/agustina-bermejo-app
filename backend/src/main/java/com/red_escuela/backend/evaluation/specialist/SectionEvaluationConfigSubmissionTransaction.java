package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.domain.SectionRef;
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
    private final SectionEvaluationConfigDueDaysReader dueDaysReader;

    public SectionEvaluationConfigSubmissionTransaction(SectionEvaluationConfigRepository configRepository,
                                                        SectionEvaluationConfigFinder configFinder,
                                                        SectionEvaluationConfigInstructorAccessChecker instructorAccess,
                                                        ApplicationEventPublisher eventPublisher,
                                                        SectionEvaluationConfigDueDaysReader dueDaysReader) {
        this.configRepository = configRepository;
        this.configFinder = configFinder;
        this.instructorAccess = instructorAccess;
        this.eventPublisher = eventPublisher;
        this.dueDaysReader = dueDaysReader;
    }

    @Transactional
    public SectionEvaluationConfig submit(SectionRef section, Long instructorId) {
        instructorAccess.requireLeadInstructor(section, instructorId);
        SectionEvaluationConfig config = configFinder.findOrThrow(section);
        config.withEvaluationConfigDueDays(dueDaysReader.read()).submitForApproval(ApplicationTime.now());
        SectionEvaluationConfig saved = configRepository.save(config);
        eventPublisher.publishEvent(SectionEvaluationConfigProposedEvent.proposedFor(saved));
        return saved;
    }
}
