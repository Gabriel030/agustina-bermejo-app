package com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig;

import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.evaluation.dto.CreateExamBoardRequest;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.evaluation.exception.InvalidSectionEvaluationConfigTransitionException;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import com.red_escuela.backend.evaluation.service.ExamBoardService;
import com.red_escuela.backend.evaluation.service.SectionEvaluationConfigFinder;
import com.red_escuela.backend.evaluation.service.SectionEvaluationConfigInstructorAccess;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.SectionEvaluationConfigApprovedEvent;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@UseCase
public class ApproveSectionEvaluationConfigService implements ApproveSectionEvaluationConfigUseCase {

    private final SectionEvaluationConfigRepository configRepository;
    private final SectionEvaluationConfigFinder configFinder;
    private final SectionEvaluationConfigInstructorAccess instructorAccess;
    private final ExamBoardService examBoardService;
    private final ApplicationEventPublisher eventPublisher;

    public ApproveSectionEvaluationConfigService(SectionEvaluationConfigRepository configRepository,
                                                  SectionEvaluationConfigFinder configFinder,
                                                  SectionEvaluationConfigInstructorAccess instructorAccess,
                                                  ExamBoardService examBoardService,
                                                  ApplicationEventPublisher eventPublisher) {
        this.configRepository = configRepository;
        this.configFinder = configFinder;
        this.instructorAccess = instructorAccess;
        this.examBoardService = examBoardService;
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
        config.setReviewedAt(LocalDateTime.now());
        SectionEvaluationConfig saved = configRepository.save(config);

        SubjectSection section = config.getSubjectSection();
        Term term = section.getTerm();
        createFinalCall(section, term, config.getFinalCall1Date());
        createFinalCall(section, term, config.getFinalCall2Date());

        Person leadInstructorPerson = instructorAccess.leadInstructorPerson(section);
        eventPublisher.publishEvent(new SectionEvaluationConfigApprovedEvent(saved, leadInstructorPerson));
        return saved;
    }

    private void createFinalCall(SubjectSection section, Term term, LocalDate examDate) {
        if (examDate == null) {
            return;
        }
        examBoardService.crear(new CreateExamBoardRequest(
                section.getSubject().getId(), null, term.getId(),
                examDate.atStartOfDay(), null, null, null));
    }
}
