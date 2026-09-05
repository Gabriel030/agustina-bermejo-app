package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateExamBoardRequest;
import com.red_escuela.backend.dto.RejectSectionEvaluationConfigRequest;
import com.red_escuela.backend.dto.SectionInstructorSummary;
import com.red_escuela.backend.dto.UpdateSectionEvaluationConfigRequest;
import com.red_escuela.backend.entity.InstructorRole;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.SectionEvaluationConfig;
import com.red_escuela.backend.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.entity.SubjectSection;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.exception.InvalidEvaluationDateException;
import com.red_escuela.backend.exception.InvalidSectionEvaluationConfigTransitionException;
import com.red_escuela.backend.exception.NotOwnSectionException;
import com.red_escuela.backend.exception.NotSectionLeadInstructorException;
import com.red_escuela.backend.exception.SectionEvaluationConfigNotFoundException;
import com.red_escuela.backend.exception.SubmissionWindowClosedException;
import com.red_escuela.backend.notification.SectionEvaluationConfigApprovedEvent;
import com.red_escuela.backend.notification.SectionEvaluationConfigProposedEvent;
import com.red_escuela.backend.notification.SectionEvaluationConfigRejectedEvent;
import com.red_escuela.backend.repository.HolidayRepository;
import com.red_escuela.backend.repository.SectionEvaluationConfigRepository;
import com.red_escuela.backend.repository.SubjectSectionInstructorRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SectionEvaluationConfigService {

    private final SectionEvaluationConfigRepository configRepository;
    private final SubjectSectionInstructorRepository sectionInstructorRepository;
    private final HolidayRepository holidayRepository;
    private final ExamBoardService examBoardService;
    private final ApplicationEventPublisher eventPublisher;

    public SectionEvaluationConfigService(
            SectionEvaluationConfigRepository configRepository,
            SubjectSectionInstructorRepository sectionInstructorRepository,
            HolidayRepository holidayRepository,
            ExamBoardService examBoardService,
            ApplicationEventPublisher eventPublisher) {
        this.configRepository = configRepository;
        this.sectionInstructorRepository = sectionInstructorRepository;
        this.holidayRepository = holidayRepository;
        this.examBoardService = examBoardService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public SectionEvaluationConfig createInitialDraft(SubjectSection section) {
        SectionEvaluationConfig config = new SectionEvaluationConfig();
        config.setSubjectSection(section);
        config.setStatus(SectionEvaluationConfigStatus.DRAFT);
        return configRepository.save(config);
    }

    public SectionEvaluationConfig getBySection(Long sectionId, Long instructorId) {
        if (!sectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId)) {
            throw new NotOwnSectionException(instructorId, sectionId);
        }
        return findOrThrow(sectionId);
    }

    public SectionEvaluationConfig getBySectionForStaff(Long sectionId) {
        return findOrThrow(sectionId);
    }

    @Transactional
    public SectionEvaluationConfig saveDraft(Long sectionId, Long instructorId, UpdateSectionEvaluationConfigRequest request) {
        validateLeadInstructor(sectionId, instructorId);
        SectionEvaluationConfig config = findOrThrow(sectionId);
        if (config.getStatus() != SectionEvaluationConfigStatus.DRAFT
                && config.getStatus() != SectionEvaluationConfigStatus.REJECTED) {
            throw new InvalidSectionEvaluationConfigTransitionException(config.getStatus(), "editar");
        }
        Term term = config.getSubjectSection().getTerm();
        validateDate(term, request.tp1DueDate());
        validateDate(term, request.tp2DueDate());
        validateDate(term, request.tp3DueDate());
        validateDate(term, request.exam1Date());
        validateDate(term, request.exam2Date());
        validateDate(term, request.exam3Date());
        validateDate(term, request.exam4Date());
        validateDate(term, request.finalCall1Date());
        validateDate(term, request.finalCall2Date());

        config.setTpCount(request.tpCount());
        config.setTp1DueDate(request.tp1DueDate());
        config.setTp2DueDate(request.tp2DueDate());
        config.setTp3DueDate(request.tp3DueDate());
        config.setExam1Date(request.exam1Date());
        config.setExam2Date(request.exam2Date());
        config.setExam3Date(request.exam3Date());
        config.setExam4Date(request.exam4Date());
        config.setFinalCall1Date(request.finalCall1Date());
        config.setFinalCall2Date(request.finalCall2Date());
        if (config.getStatus() == SectionEvaluationConfigStatus.REJECTED) {
            config.setStatus(SectionEvaluationConfigStatus.DRAFT);
            config.setRejectionReason(null);
        }
        return configRepository.save(config);
    }

    @Transactional
    public SectionEvaluationConfig submitForApproval(Long sectionId, Long instructorId) {
        validateLeadInstructor(sectionId, instructorId);
        SectionEvaluationConfig config = findOrThrow(sectionId);
        if (config.getStatus() != SectionEvaluationConfigStatus.DRAFT) {
            throw new InvalidSectionEvaluationConfigTransitionException(config.getStatus(), "enviar a aprobación");
        }
        Term term = config.getSubjectSection().getTerm();
        if (term.getStartDate() != null && !LocalDateTime.now().isBefore(term.getStartDate())) {
            throw new SubmissionWindowClosedException(sectionId);
        }
        config.setStatus(SectionEvaluationConfigStatus.PENDING_APPROVAL);
        config.setSubmittedAt(LocalDateTime.now());
        SectionEvaluationConfig saved = configRepository.save(config);
        eventPublisher.publishEvent(new SectionEvaluationConfigProposedEvent(saved));
        return saved;
    }

    @Transactional
    public SectionEvaluationConfig approve(Long sectionId, Person actor) {
        SectionEvaluationConfig config = findOrThrow(sectionId);
        if (config.getStatus() != SectionEvaluationConfigStatus.PENDING_APPROVAL) {
            throw new InvalidSectionEvaluationConfigTransitionException(config.getStatus(), "approve");
        }
        config.setStatus(SectionEvaluationConfigStatus.APPROVED);
        config.setReviewedBy(actor);
        config.setReviewedAt(LocalDateTime.now());
        SectionEvaluationConfig saved = configRepository.save(config);

        SubjectSection section = config.getSubjectSection();
        Term term = section.getTerm();
        createFinalCall(section, term, config.getFinalCall1Date());
        createFinalCall(section, term, config.getFinalCall2Date());

        Person leadInstructorPerson = leadInstructorPerson(section);
        eventPublisher.publishEvent(new SectionEvaluationConfigApprovedEvent(saved, leadInstructorPerson));
        return saved;
    }

    @Transactional
    public SectionEvaluationConfig reject(Long sectionId, Person actor, RejectSectionEvaluationConfigRequest request) {
        SectionEvaluationConfig config = findOrThrow(sectionId);
        if (config.getStatus() != SectionEvaluationConfigStatus.PENDING_APPROVAL) {
            throw new InvalidSectionEvaluationConfigTransitionException(config.getStatus(), "reject");
        }
        config.setStatus(SectionEvaluationConfigStatus.REJECTED);
        config.setRejectionReason(request.reason());
        config.setReviewedBy(actor);
        config.setReviewedAt(LocalDateTime.now());
        SectionEvaluationConfig saved = configRepository.save(config);

        Person leadInstructorPerson = leadInstructorPerson(config.getSubjectSection());
        eventPublisher.publishEvent(new SectionEvaluationConfigRejectedEvent(saved, leadInstructorPerson));
        return saved;
    }

    public List<SectionEvaluationConfig> listByInstructor(Long instructorId) {
        return configRepository.findBySubjectSection_Instructors_InstructorId(instructorId);
    }

    public List<SectionEvaluationConfig> listByStatus(SectionEvaluationConfigStatus status) {
        return configRepository.findByStatus(status);
    }

    public boolean isLeadInstructor(Long sectionId, Long instructorId) {
        return sectionInstructorRepository.existsBySectionIdAndInstructorIdAndRole(sectionId, instructorId, InstructorRole.LEAD);
    }

    public List<SectionInstructorSummary> listInstructors(Long sectionId) {
        return sectionInstructorRepository.findBySectionId(sectionId).stream()
                .map(SectionInstructorSummary::from)
                .toList();
    }

    private void createFinalCall(SubjectSection section, Term term, LocalDate examDate) {
        if (examDate == null) {
            return;
        }
        examBoardService.create(new CreateExamBoardRequest(
                section.getSubject().getId(), null, term.getId(),
                examDate.atStartOfDay(), null, null, null));
    }

    private Person leadInstructorPerson(SubjectSection section) {
        return sectionInstructorRepository.findBySectionId(section.getId()).stream()
                .filter(instructor -> instructor.getRole() == InstructorRole.LEAD)
                .findFirst()
                .map(instructor -> instructor.getInstructor().getPerson())
                .orElseThrow(() -> new NotOwnSectionException(null, section.getId()));
    }

    private void validateLeadInstructor(Long sectionId, Long instructorId) {
        if (!sectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId)) {
            throw new NotOwnSectionException(instructorId, sectionId);
        }
        if (!sectionInstructorRepository.existsBySectionIdAndInstructorIdAndRole(sectionId, instructorId, InstructorRole.LEAD)) {
            throw new NotSectionLeadInstructorException(instructorId, sectionId);
        }
    }

    private void validateDate(Term term, LocalDate date) {
        if (date == null) {
            return;
        }
        LocalDate start = term.getStartDate() != null ? term.getStartDate().toLocalDate() : null;
        LocalDate end = term.getEndDate() != null ? term.getEndDate().toLocalDate() : null;
        if ((start != null && date.isBefore(start)) || (end != null && date.isAfter(end))) {
            throw new InvalidEvaluationDateException(date, "fuera del rango del cuatrimestre");
        }
        if (holidayRepository.existsByDate(date)) {
            throw new InvalidEvaluationDateException(date, "coincide con un feriado");
        }
    }

    private SectionEvaluationConfig findOrThrow(Long sectionId) {
        return configRepository.findBySubjectSectionId(sectionId)
                .orElseThrow(() -> new SectionEvaluationConfigNotFoundException(sectionId));
    }
}
