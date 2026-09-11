package com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.evaluation.exception.InvalidSectionEvaluationConfigTransitionException;
import com.red_escuela.backend.evaluation.exception.SubmissionWindowClosedException;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import com.red_escuela.backend.evaluation.service.SectionEvaluationConfigFinder;
import com.red_escuela.backend.evaluation.service.SectionEvaluationConfigInstructorAccess;
import com.red_escuela.backend.notification.SectionEvaluationConfigProposedEvent;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@UseCase
public class SubmitSectionEvaluationConfigForApprovalService implements SubmitSectionEvaluationConfigForApprovalUseCase {

    private final SectionEvaluationConfigRepository configRepository;
    private final SectionEvaluationConfigFinder configFinder;
    private final SectionEvaluationConfigInstructorAccess instructorAccess;
    private final ApplicationEventPublisher eventPublisher;

    public SubmitSectionEvaluationConfigForApprovalService(SectionEvaluationConfigRepository configRepository,
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
    public SectionEvaluationConfig submit(SubmitSectionEvaluationConfigForApprovalCommand command) {
        instructorAccess.requireLeadInstructor(command.sectionId(), command.instructorId());
        SectionEvaluationConfig config = configFinder.findOrThrow(command.sectionId());
        if (config.getStatus() != SectionEvaluationConfigStatus.DRAFT) {
            throw new InvalidSectionEvaluationConfigTransitionException(config.getStatus(), "enviar a aprobación");
        }
        Term term = config.getSubjectSection().getTerm();
        if (term.getStartDate() != null && !LocalDateTime.now().isBefore(term.getStartDate())) {
            throw new SubmissionWindowClosedException(command.sectionId());
        }
        config.setStatus(SectionEvaluationConfigStatus.PENDING_APPROVAL);
        config.setSubmittedAt(LocalDateTime.now());
        SectionEvaluationConfig saved = configRepository.save(config);
        eventPublisher.publishEvent(new SectionEvaluationConfigProposedEvent(saved));
        return saved;
    }
}
