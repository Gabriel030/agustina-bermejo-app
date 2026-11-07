package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.academicstructure.api.SectionTermEndFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.evaluation.dto.CorrectGradeItemRequest;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemAudit;
import com.red_escuela.backend.evaluation.entity.GradeItemAuditKind;
import com.red_escuela.backend.evaluation.entity.SectionGradeItemPublication;
import com.red_escuela.backend.evaluation.events.GradeCorrectedEvent;
import com.red_escuela.backend.evaluation.exception.GradeCorrectionNoChangeException;
import com.red_escuela.backend.evaluation.exception.GradeCorrectionPeriodEndedException;
import com.red_escuela.backend.evaluation.exception.GradeItemNotFoundException;
import com.red_escuela.backend.evaluation.exception.GradeItemNotPublishedException;
import com.red_escuela.backend.evaluation.exception.InvalidGradeCorrectionReasonException;
import com.red_escuela.backend.evaluation.repository.GradeItemAuditRepository;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import com.red_escuela.backend.evaluation.repository.SectionGradeItemPublicationRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class PublishedGradeCorrector {

    public static final int MIN_REASON_LENGTH = 5;
    public static final int MAX_REASON_LENGTH = 500;

    private final GradeItemRepository gradeItemRepository;
    private final SectionGradeItemPublicationRepository publicationRepository;
    private final GradeItemAuditRepository auditRepository;
    private final SectionEnrollmentFinder sectionEnrollmentFinder;
    private final SectionTermEndFinder sectionTermEndFinder;
    private final GradeItemService gradeItemService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PublishedGradeCorrector(GradeItemRepository gradeItemRepository,
                                   SectionGradeItemPublicationRepository publicationRepository,
                                   GradeItemAuditRepository auditRepository,
                                   SectionEnrollmentFinder sectionEnrollmentFinder,
                                   SectionTermEndFinder sectionTermEndFinder,
                                   GradeItemService gradeItemService,
                                   ApplicationEventPublisher eventPublisher,
                                   Clock clock) {
        this.gradeItemRepository = gradeItemRepository;
        this.publicationRepository = publicationRepository;
        this.auditRepository = auditRepository;
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
        this.sectionTermEndFinder = sectionTermEndFinder;
        this.gradeItemService = gradeItemService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public GradeItemAudit correct(Long gradeItemId, CorrectGradeItemRequest request, Long actorPersonId) {
        String reason = normalizedReason(request.reason());
        GradeItem gradeItem = gradeItemRepository.findById(gradeItemId)
                .orElseThrow(() -> new GradeItemNotFoundException(gradeItemId));
        Target target = Target.of(gradeItem);
        lockEnrollment(target);
        requirePublished(gradeItem, target);
        requireWithinTerm(gradeItemId, target);
        if (request.newScore().equals(gradeItem.getScore())) {
            throw new GradeCorrectionNoChangeException(gradeItemId);
        }
        GradeItemAudit audit = auditRepository.save(GradeItemAudit.builder()
                .gradeItemId(gradeItemId)
                .sectionKind(target.kind())
                .sectionId(target.sectionId())
                .enrollmentId(target.enrollmentId())
                .gradeItemType(gradeItem.getType())
                .sequenceNumber(SectionGradeItemPublication.sequenceKey(gradeItem.getSequenceNumber()))
                .previousScore(gradeItem.getScore())
                .newScore(request.newScore())
                .reason(reason)
                .changedById(actorPersonId)
                .changedAt(LocalDateTime.now(clock))
                .kind(GradeItemAuditKind.CORRECTION)
                .build());
        gradeItem.setScore(request.newScore());
        gradeItemRepository.save(gradeItem);
        recalculateCondition(target);
        eventPublisher.publishEvent(GradeCorrectedEvent.correctedFor(
                gradeItemId, target.studentPersonId(), target.sectionName(), gradeItem.getType().displayName(),
                SectionGradeItemPublication.sequenceKey(gradeItem.getSequenceNumber())));
        return audit;
    }

    private String normalizedReason(String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.length() < MIN_REASON_LENGTH || trimmed.length() > MAX_REASON_LENGTH) {
            throw new InvalidGradeCorrectionReasonException(MIN_REASON_LENGTH, MAX_REASON_LENGTH);
        }
        return trimmed;
    }

    private void lockEnrollment(Target target) {
        switch (target.kind()) {
            case PROGRAM -> sectionEnrollmentFinder.lockProgramEnrollment(target.enrollmentId());
            case COURSE -> sectionEnrollmentFinder.lockCourseEnrollment(target.enrollmentId());
        }
    }

    private void recalculateCondition(Target target) {
        switch (target.kind()) {
            case PROGRAM -> gradeItemService.recalculateProgramEnrollmentCondition(target.enrollmentId());
            case COURSE -> gradeItemService.recalculateCourseEnrollmentCondition(target.enrollmentId());
        }
    }

    private void requirePublished(GradeItem gradeItem, Target target) {
        boolean published = publicationRepository.existsBySectionKindAndSectionIdAndGradeItemTypeAndSequenceNumber(
                target.kind(), target.sectionId(), gradeItem.getType(),
                SectionGradeItemPublication.sequenceKey(gradeItem.getSequenceNumber()));
        if (!published) {
            throw new GradeItemNotPublishedException(gradeItem.getId());
        }
    }

    private void requireWithinTerm(Long gradeItemId, Target target) {
        LocalDate termEndDate = sectionTermEndFinder.termEndDate(target.kind(), target.sectionId());
        if (LocalDate.now(clock).isAfter(termEndDate)) {
            throw new GradeCorrectionPeriodEndedException(gradeItemId, termEndDate);
        }
    }

    private record Target(SectionKind kind, Long sectionId, Long enrollmentId, Long studentPersonId, String sectionName) {

        static Target of(GradeItem gradeItem) {
            if (gradeItem.getProgramSubjectSectionEnrollment() != null) {
                var enrollment = gradeItem.getProgramSubjectSectionEnrollment();
                return new Target(SectionKind.PROGRAM, enrollment.getSubjectSection().getId(), enrollment.getId(),
                        enrollment.getStudent().getPerson().getId(), enrollment.getSubjectSection().getName());
            }
            var enrollment = gradeItem.getCourseSubjectSectionEnrollment();
            return new Target(SectionKind.COURSE, enrollment.getCourseSubjectSection().getId(), enrollment.getId(),
                    enrollment.getStudent().getPerson().getId(), enrollment.getCourseSubjectSection().getName());
        }
    }
}
