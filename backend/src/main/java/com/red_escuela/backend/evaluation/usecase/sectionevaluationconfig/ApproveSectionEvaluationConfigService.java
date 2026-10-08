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
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.repository.SectionScheduleSlotRepository;
import com.red_escuela.backend.shared.time.ApplicationTime;

@UseCase
public class ApproveSectionEvaluationConfigService implements ApproveSectionEvaluationConfigUseCase {

    private final SectionEvaluationConfigRepository configRepository;
    private final SectionEvaluationConfigFinder configFinder;
    private final SectionEvaluationConfigInstructorAccess instructorAccess;
    private final ExamBoardService examBoardService;
    private final ApplicationEventPublisher eventPublisher;
    private final SectionScheduleSlotRepository scheduleSlotRepository;

    private static final int REGISTRATION_CLOSES_DAYS_BEFORE_EXAM = 2;
    private static final LocalTime REGISTRATION_CLOSING_TIME = LocalTime.of(23, 59);

    public ApproveSectionEvaluationConfigService(SectionEvaluationConfigRepository configRepository,
                                                  SectionEvaluationConfigFinder configFinder,
                                                  SectionEvaluationConfigInstructorAccess instructorAccess,
                                                  ExamBoardService examBoardService,
                                                  ApplicationEventPublisher eventPublisher,
                                                  SectionScheduleSlotRepository scheduleSlotRepository) {
        this.configRepository = configRepository;
        this.configFinder = configFinder;
        this.instructorAccess = instructorAccess;
        this.examBoardService = examBoardService;
        this.eventPublisher = eventPublisher;
        this.scheduleSlotRepository = scheduleSlotRepository;
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

        SubjectSection section = config.getSubjectSection();
        Term term = section.getTerm();
        List<SectionScheduleSlot> slots = scheduleSlotRepository.findBySubjectSectionId(section.getId());
        LocalDateTime approvedAt = config.getReviewedAt();
        createFinalCall(section, term, slots, config.getFinalCall1Date(), approvedAt);
        createFinalCall(section, term, slots, config.getFinalCall2Date(), approvedAt);

        Person leadInstructorPerson = instructorAccess.leadInstructorPerson(section);
        eventPublisher.publishEvent(new SectionEvaluationConfigApprovedEvent(saved, leadInstructorPerson));
        return saved;
    }

    private void createFinalCall(SubjectSection section, Term term, List<SectionScheduleSlot> slots,
                                 LocalDate examDay, LocalDateTime approvedAt) {
        if (examDay == null) {
            return;
        }
        Optional<SectionScheduleSlot> slot = slotFor(slots, examDay);
        LocalDateTime examDate = slot.map(s -> examDay.atTime(s.getStartTime())).orElse(examDay.atStartOfDay());
        String classroom = slot.map(s -> s.getClassroom().getName()).orElse(null);
        LocalDateTime registrationEnd = examDay.minusDays(REGISTRATION_CLOSES_DAYS_BEFORE_EXAM).atTime(REGISTRATION_CLOSING_TIME);
        examBoardService.create(new CreateExamBoardRequest(
                section.getSubject().getId(), null, term.getId(),
                examDate, approvedAt, registrationEnd, classroom));
    }

    private Optional<SectionScheduleSlot> slotFor(List<SectionScheduleSlot> slots, LocalDate examDay) {
        Comparator<SectionScheduleSlot> byWeekTime = Comparator.comparing(SectionScheduleSlot::getDayOfWeek)
                .thenComparing(SectionScheduleSlot::getStartTime);
        return slots.stream()
                .filter(s -> s.getDayOfWeek() == examDay.getDayOfWeek())
                .min(byWeekTime)
                .or(() -> slots.stream().min(byWeekTime));
    }
}
