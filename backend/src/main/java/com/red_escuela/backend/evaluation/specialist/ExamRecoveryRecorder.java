package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.SectionTeachingFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.evaluation.api.GraduationRequestTriggerPort;
import com.red_escuela.backend.evaluation.dto.GradeItemAuditResponse;
import com.red_escuela.backend.evaluation.dto.RecordExamRecoveryRequest;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemAudit;
import com.red_escuela.backend.evaluation.entity.GradeItemAuditKind;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.entity.SectionGradeItemPublication;
import com.red_escuela.backend.evaluation.events.GradeCorrectedEvent;
import com.red_escuela.backend.evaluation.exception.GradeItemAlreadyRecoveredException;
import com.red_escuela.backend.evaluation.exception.GradeItemNotFoundException;
import com.red_escuela.backend.evaluation.exception.GradeItemNotPublishedException;
import com.red_escuela.backend.evaluation.exception.GradeItemNotRecoverableException;
import com.red_escuela.backend.evaluation.exception.RecoveryQuotaExceededException;
import com.red_escuela.backend.academicstructure.exception.NotOwnSectionException;
import com.red_escuela.backend.evaluation.repository.GradeItemAuditRepository;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import com.red_escuela.backend.evaluation.repository.SectionGradeItemPublicationRepository;
import com.red_escuela.backend.shared.domain.Grade;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ExamRecoveryRecorder {

    static final String RECOVERY_REASON = "Recuperatorio del parcial";

    private final GradeItemRepository gradeItemRepository;
    private final SectionGradeItemPublicationRepository publicationRepository;
    private final GradeItemAuditRepository auditRepository;
    private final SectionEnrollmentFinder sectionEnrollmentFinder;
    private final SectionTeachingFinder sectionTeachingFinder;
    private final CourseworkApprovalChecker courseworkApprovalChecker;
    private final GradeItemRecorder gradeItemRecorder;
    private final GraduationRequestTriggerPort graduationRequestTrigger;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ExamRecoveryRecorder(GradeItemRepository gradeItemRepository,
                                SectionGradeItemPublicationRepository publicationRepository,
                                GradeItemAuditRepository auditRepository,
                                SectionEnrollmentFinder sectionEnrollmentFinder,
                                SectionTeachingFinder sectionTeachingFinder,
                                CourseworkApprovalChecker courseworkApprovalChecker,
                                GradeItemRecorder gradeItemRecorder,
                                GraduationRequestTriggerPort graduationRequestTrigger,
                                ApplicationEventPublisher eventPublisher,
                                Clock clock) {
        this.gradeItemRepository = gradeItemRepository;
        this.publicationRepository = publicationRepository;
        this.auditRepository = auditRepository;
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
        this.sectionTeachingFinder = sectionTeachingFinder;
        this.courseworkApprovalChecker = courseworkApprovalChecker;
        this.gradeItemRecorder = gradeItemRecorder;
        this.graduationRequestTrigger = graduationRequestTrigger;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public GradeItemAuditResponse record(Long gradeItemId, Long instructorId, Long actorPersonId,
                                         RecordExamRecoveryRequest request) {
        GradeItem gradeItem = gradeItemRepository.findById(gradeItemId)
                .orElseThrow(() -> new GradeItemNotFoundException(gradeItemId));
        Target target = Target.of(gradeItem);
        lockEnrollment(target);
        requireTeaches(target, instructorId);
        requireRecoverable(gradeItem, target);
        List<GradeItem> enrollmentItems = enrollmentItems(target);
        requireQuotaAvailable(target.scheme(), enrollmentItems);
        courseworkApprovalChecker.requireApprovedBeforeExam(
                target.kind(), target.sectionId(), target.scheme(), enrollmentItems);
        Grade recoveryScore = Grade.of(request.score());
        Grade originalScore = gradeItem.getScore();
        LocalDateTime now = LocalDateTime.now(clock);
        gradeItem.applyRecovery(recoveryScore, actorPersonId, now);
        gradeItemRepository.save(gradeItem);
        GradeItemAudit audit = auditRepository.save(GradeItemAudit.builder()
                .gradeItemId(gradeItemId)
                .sectionKind(target.kind())
                .sectionId(target.sectionId())
                .enrollmentId(target.enrollmentId())
                .gradeItemType(gradeItem.getType())
                .sequenceNumber(SectionGradeItemPublication.sequenceKey(gradeItem.getSequenceNumber()))
                .previousScore(originalScore)
                .newScore(recoveryScore)
                .reason(RECOVERY_REASON)
                .changedById(actorPersonId)
                .changedAt(now)
                .kind(GradeItemAuditKind.RECOVERY)
                .build());
        recalculateCondition(target);
        flagGraduationRequestOutdated(target);
        eventPublisher.publishEvent(GradeCorrectedEvent.correctedFor(
                gradeItemId, target.studentPersonId(), target.sectionName(), gradeItem.getType().displayName(),
                SectionGradeItemPublication.sequenceKey(gradeItem.getSequenceNumber())));
        return GradeItemAuditResponse.from(audit);
    }

    private void requireTeaches(Target target, Long instructorId) {
        if (!sectionTeachingFinder.teaches(target.kind(), target.sectionId(), instructorId)) {
            throw new NotOwnSectionException(instructorId, target.sectionId());
        }
    }

    private void requireRecoverable(GradeItem gradeItem, Target target) {
        if (gradeItem.getType() != GradeItemType.EXAM) {
            throw new GradeItemNotRecoverableException(gradeItem.getId());
        }
        boolean published = publicationRepository.existsBySectionKindAndSectionIdAndGradeItemTypeAndSequenceNumber(
                target.kind(), target.sectionId(), gradeItem.getType(),
                SectionGradeItemPublication.sequenceKey(gradeItem.getSequenceNumber()));
        if (!published) {
            throw new GradeItemNotPublishedException(gradeItem.getId());
        }
        if (gradeItem.isRecovered()) {
            throw new GradeItemAlreadyRecoveredException(gradeItem.getId());
        }
    }

    private void requireQuotaAvailable(EvaluationScheme scheme, List<GradeItem> enrollmentItems) {
        long used = enrollmentItems.stream().filter(GradeItem::isRecovered).count();
        if (used >= scheme.getRecoveryQuota()) {
            throw new RecoveryQuotaExceededException(scheme.getRecoveryQuota());
        }
    }

    private List<GradeItem> enrollmentItems(Target target) {
        return switch (target.kind()) {
            case PROGRAM -> gradeItemRepository.findByProgramSubjectSectionEnrollmentId(target.enrollmentId());
            case COURSE -> gradeItemRepository.findByCourseSubjectSectionEnrollmentId(target.enrollmentId());
        };
    }

    private void lockEnrollment(Target target) {
        switch (target.kind()) {
            case PROGRAM -> sectionEnrollmentFinder.lockProgramEnrollment(target.enrollmentId());
            case COURSE -> sectionEnrollmentFinder.lockCourseEnrollment(target.enrollmentId());
        }
    }

    private void recalculateCondition(Target target) {
        switch (target.kind()) {
            case PROGRAM -> gradeItemRecorder.recalculateProgramEnrollmentCondition(target.enrollmentId());
            case COURSE -> gradeItemRecorder.recalculateCourseEnrollmentCondition(target.enrollmentId());
        }
    }

    private void flagGraduationRequestOutdated(Target target) {
        if (target.kind() == SectionKind.PROGRAM) {
            graduationRequestTrigger.flagGraduationRequestOutdated(target.programEnrollmentId());
        }
    }

    private record Target(SectionKind kind, Long sectionId, Long enrollmentId, Long programEnrollmentId, Long studentPersonId,
                          String sectionName, EvaluationScheme scheme) {

        static Target of(GradeItem gradeItem) {
            if (gradeItem.getProgramSubjectSectionEnrollment() != null) {
                var enrollment = gradeItem.getProgramSubjectSectionEnrollment();
                return new Target(SectionKind.PROGRAM, enrollment.getSubjectSection().getId(), enrollment.getId(),
                        enrollment.getProgramEnrollment().getId(), enrollment.getStudent().getPerson().getId(),
                        enrollment.getSubjectSection().getName(), enrollment.getSubjectSection().getEvaluationScheme());
            }
            var enrollment = gradeItem.getCourseSubjectSectionEnrollment();
            return new Target(SectionKind.COURSE, enrollment.getCourseSubjectSection().getId(), enrollment.getId(), null,
                    enrollment.getStudent().getPerson().getId(), enrollment.getCourseSubjectSection().getName(),
                    enrollment.getCourseSubjectSection().getEvaluationScheme());
        }
    }
}
