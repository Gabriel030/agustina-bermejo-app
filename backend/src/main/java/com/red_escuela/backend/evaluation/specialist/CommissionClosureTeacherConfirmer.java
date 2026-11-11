package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.api.SectionTeachingFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.domain.ClosureSectionKind;
import com.red_escuela.backend.evaluation.domain.ClosureSnapshotResult;
import com.red_escuela.backend.evaluation.entity.ClosureAuditAction;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import com.red_escuela.backend.evaluation.entity.CommissionClosureAudit;
import com.red_escuela.backend.evaluation.exception.ClosureAlreadyClosedException;
import com.red_escuela.backend.evaluation.exception.ClosureAlreadyConfirmedException;
import com.red_escuela.backend.evaluation.exception.ClosureNotAvailableException;
import com.red_escuela.backend.evaluation.exception.PublishedGradesIncompleteException;
import com.red_escuela.backend.evaluation.repository.CommissionClosureAuditRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class CommissionClosureTeacherConfirmer {

    private final SectionFinder sectionFinder;
    private final SectionTeachingFinder sectionTeachingFinder;
    private final CommissionClosureRepository closureRepository;
    private final CommissionClosureAuditRepository auditRepository;
    private final CommissionClosureSnapshotRecorder snapshotRecorder;
    private final ClosureColumnsFinder closureColumnsFinder;
    private final CurrentActorFinder currentActorFinder;
    private final Clock clock;

    public CommissionClosureTeacherConfirmer(SectionFinder sectionFinder, SectionTeachingFinder sectionTeachingFinder,
            CommissionClosureRepository closureRepository, CommissionClosureAuditRepository auditRepository,
            CommissionClosureSnapshotRecorder snapshotRecorder, ClosureColumnsFinder closureColumnsFinder,
            CurrentActorFinder currentActorFinder, Clock clock) {
        this.sectionFinder = sectionFinder;
        this.sectionTeachingFinder = sectionTeachingFinder;
        this.closureRepository = closureRepository;
        this.auditRepository = auditRepository;
        this.snapshotRecorder = snapshotRecorder;
        this.closureColumnsFinder = closureColumnsFinder;
        this.currentActorFinder = currentActorFinder;
        this.clock = clock;
    }

    @Transactional
    public void confirmClosure(ClosureSectionKind sectionKind, Long sectionId, Long instructorId) {
        SectionKind kind = sectionKind.toSectionKind();
        sectionFinder.requireExists(kind, sectionId);
        sectionTeachingFinder.requireTeaches(kind, sectionId, instructorId);
        Long closureId = closureRepository.findBySectionKindAndSectionId(kind, sectionId)
                .orElseThrow(() -> new ClosureNotAvailableException(sectionId)).getId();
        CommissionClosure closure = closureRepository.findByIdForUpdate(closureId).orElseThrow();
        requireOpenForTeacher(closure);
        if (!closureColumnsFinder.allPublished(closureColumnsFinder.findColumns(kind, sectionId))) {
            throw new PublishedGradesIncompleteException(sectionId);
        }
        ClosureSnapshotResult snapshot = snapshotRecorder.recordSnapshot(closureId);
        if (!snapshot.pendingEnrollmentIds().isEmpty()) {
            throw new PublishedGradesIncompleteException(sectionId);
        }
        Long actorPersonId = currentActorFinder.currentPersonId();
        LocalDateTime now = LocalDateTime.now(clock);
        closure.confirmByTeacher(actorPersonId, now);
        closureRepository.save(closure);
        auditRepository.save(CommissionClosureAudit.builder()
                .closureId(closureId)
                .action(ClosureAuditAction.CONFIRMED_BY_TEACHER)
                .reportVersion(closure.getReportVersion())
                .actorPersonId(actorPersonId)
                .occurredAt(now)
                .build());
    }

    private void requireOpenForTeacher(CommissionClosure closure) {
        if (closure.getStatus() == ClosureStatus.TEACHER_CONFIRMED) {
            throw new ClosureAlreadyConfirmedException(closure.getId());
        }
        if (closure.getStatus() == ClosureStatus.CLOSED) {
            throw new ClosureAlreadyClosedException(closure.getId());
        }
    }
}
