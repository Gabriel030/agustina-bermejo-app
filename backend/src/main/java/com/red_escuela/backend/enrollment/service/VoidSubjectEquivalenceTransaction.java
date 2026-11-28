package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.academicstructure.api.ProgramSubjectView;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalence;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalenceStatus;
import com.red_escuela.backend.enrollment.events.SubjectEquivalenceVoidedEvent;
import com.red_escuela.backend.enrollment.exception.EquivalenceAlreadyVoidedException;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.exception.SubjectEquivalenceNotFoundException;
import com.red_escuela.backend.enrollment.policy.SubjectEquivalencePolicy;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectEquivalenceRepository;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class VoidSubjectEquivalenceTransaction {

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final SubjectEquivalenceRepository subjectEquivalenceRepository;
    private final ProgramCurriculumFinder programCurriculumFinder;
    private final StaffPersonResolver staffPersonResolver;
    private final AuditTrail auditTrail;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public VoidSubjectEquivalenceTransaction(ProgramEnrollmentRepository programEnrollmentRepository,
                                             SubjectEquivalenceRepository subjectEquivalenceRepository,
                                             ProgramCurriculumFinder programCurriculumFinder, StaffPersonResolver staffPersonResolver, AuditTrail auditTrail,
                                             ApplicationEventPublisher eventPublisher, Clock clock) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.subjectEquivalenceRepository = subjectEquivalenceRepository;
        this.programCurriculumFinder = programCurriculumFinder;
        this.staffPersonResolver = staffPersonResolver;
        this.auditTrail = auditTrail;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    private String subjectNameOf(ProgramEnrollment enrollment, Long subjectId) {
        return programCurriculumFinder.findProgramSubject(enrollment.getProgram().getId(), subjectId)
                .map(ProgramSubjectView::subjectName)
                .orElse(null);
    }

    @Transactional
    public void voidEquivalence(Long programEnrollmentId, Long equivalenceId, String rawReason) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
        SubjectEquivalence equivalence = subjectEquivalenceRepository.findById(equivalenceId)
                .filter(found -> found.getProgramEnrollment().getId().equals(programEnrollmentId))
                .orElseThrow(() -> new SubjectEquivalenceNotFoundException(equivalenceId));
        SubjectEquivalencePolicy.requireActiveEnrollment(programEnrollmentId, enrollment.getStatus());
        SubjectEquivalencePolicy.requireActive(equivalence);
        String reason = SubjectEquivalencePolicy.normalizeReason(rawReason);
        equivalence.setStatus(SubjectEquivalenceStatus.VOIDED);
        equivalence.setVoidedById(staffPersonResolver.currentStaffPersonId());
        equivalence.setVoidedAt(LocalDateTime.now(clock));
        equivalence.setVoidReason(reason);
        try {
            subjectEquivalenceRepository.saveAndFlush(equivalence);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new EquivalenceAlreadyVoidedException(equivalenceId);
        }
        auditTrail.record(new AuditRecord(AuditAction.SUBJECT_EQUIVALENCE_VOIDED, AuditEntityType.SUBJECT_EQUIVALENCE,
                equivalenceId, null, null, reason));
        eventPublisher.publishEvent(SubjectEquivalenceVoidedEvent.voidedFor(equivalenceId, programEnrollmentId,
                equivalence.getSubjectId(), enrollment.getStudent().getPerson().getId(), subjectNameOf(enrollment, equivalence.getSubjectId())));
    }
}
