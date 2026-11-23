package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.enrollment.dto.RecordSubjectEquivalenceRequest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.StudentDocument;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalence;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalenceStatus;
import com.red_escuela.backend.enrollment.events.SubjectEquivalenceRecordedEvent;
import com.red_escuela.backend.enrollment.exception.EquivalenceAlreadyActiveException;
import com.red_escuela.backend.enrollment.exception.EquivalenceDocumentInvalidException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.exception.SubjectNotInPlanException;
import com.red_escuela.backend.enrollment.policy.SubjectEquivalencePolicy;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.StudentDocumentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectEquivalenceRepository;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import com.red_escuela.backend.shared.domain.Grade;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class RecordSubjectEquivalenceTransaction {

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final SubjectEquivalenceRepository subjectEquivalenceRepository;
    private final StudentDocumentRepository studentDocumentRepository;
    private final ProgramCurriculumFinder programCurriculumFinder;
    private final SubjectEquivalenceEligibilityGuard eligibilityGuard;
    private final StaffPersonResolver staffPersonResolver;
    private final AuditTrail auditTrail;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RecordSubjectEquivalenceTransaction(ProgramEnrollmentRepository programEnrollmentRepository,
                                               SubjectEquivalenceRepository subjectEquivalenceRepository,
                                               StudentDocumentRepository studentDocumentRepository,
                                               ProgramCurriculumFinder programCurriculumFinder,
                                               SubjectEquivalenceEligibilityGuard eligibilityGuard,
                                               StaffPersonResolver staffPersonResolver, AuditTrail auditTrail,
                                               ApplicationEventPublisher eventPublisher, Clock clock) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.subjectEquivalenceRepository = subjectEquivalenceRepository;
        this.studentDocumentRepository = studentDocumentRepository;
        this.programCurriculumFinder = programCurriculumFinder;
        this.eligibilityGuard = eligibilityGuard;
        this.staffPersonResolver = staffPersonResolver;
        this.auditTrail = auditTrail;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public Long record(Long programEnrollmentId, RecordSubjectEquivalenceRequest request) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
        SubjectEquivalencePolicy.requireActiveEnrollment(programEnrollmentId, enrollment.getStatus());
        Long subjectId = request.subjectId();
        Long programId = enrollment.getProgram().getId();
        if (programCurriculumFinder.findActiveCurriculumEntries(programId).stream()
                .noneMatch(entry -> entry.subjectId().equals(subjectId))) {
            throw new SubjectNotInPlanException(subjectId, programId);
        }
        Grade originScore = SubjectEquivalencePolicy.parseOriginScore(request.originMark());
        SubjectEquivalencePolicy.requireNotFuture(request.recognizedOn(), LocalDate.now(clock));
        String reason = SubjectEquivalencePolicy.normalizeReason(request.reason());
        requireDocumentOfStudent(programEnrollmentId, enrollment.getStudent().getId(), request.studentDocumentId());
        if (subjectEquivalenceRepository.findByProgramEnrollmentIdAndSubjectIdAndStatus(programEnrollmentId, subjectId,
                SubjectEquivalenceStatus.ACTIVE).isPresent()) {
            throw new EquivalenceAlreadyActiveException(programEnrollmentId, subjectId);
        }
        eligibilityGuard.requireSubjectOpenForEquivalence(programEnrollmentId, subjectId);
        SubjectEquivalence saved = saveEquivalence(enrollment, request, originScore, reason);
        auditTrail.record(new AuditRecord(AuditAction.SUBJECT_EQUIVALENCE_RECORDED, AuditEntityType.SUBJECT_EQUIVALENCE,
                saved.getId(), null, null, reason));
        eventPublisher.publishEvent(SubjectEquivalenceRecordedEvent.recordedFor(saved.getId(), programEnrollmentId,
                subjectId, enrollment.getStudent().getPerson().getId()));
        return saved.getId();
    }

    private SubjectEquivalence saveEquivalence(ProgramEnrollment enrollment, RecordSubjectEquivalenceRequest request,
                                               Grade originScore, String reason) {
        SubjectEquivalence equivalence = SubjectEquivalence.builder()
                .programEnrollment(enrollment)
                .subjectId(request.subjectId())
                .studentDocumentId(request.studentDocumentId())
                .originInstitution(request.originInstitution().trim())
                .originSubjectName(request.originSubjectName().trim())
                .originScore(originScore)
                .recognizedOn(request.recognizedOn())
                .resolutionNumber(request.resolutionNumber().trim())
                .reason(reason)
                .recordedById(staffPersonResolver.currentStaffPersonId())
                .recordedAt(LocalDateTime.now(clock))
                .build();
        try {
            return subjectEquivalenceRepository.saveAndFlush(equivalence);
        } catch (DataIntegrityViolationException exception) {
            throw new EquivalenceAlreadyActiveException(enrollment.getId(), request.subjectId());
        }
    }

    private void requireDocumentOfStudent(Long programEnrollmentId, Long studentId, Long documentId) {
        if (documentId == null) {
            return;
        }
        boolean belongsToStudent = studentDocumentRepository.findById(documentId)
                .map(StudentDocument::getStudent)
                .filter(student -> student.getId().equals(studentId))
                .isPresent();
        if (!belongsToStudent) {
            throw new EquivalenceDocumentInvalidException(documentId, programEnrollmentId);
        }
    }
}
