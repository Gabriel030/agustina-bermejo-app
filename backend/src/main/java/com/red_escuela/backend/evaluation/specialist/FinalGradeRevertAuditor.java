package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemAudit;
import com.red_escuela.backend.evaluation.entity.GradeItemAuditKind;
import com.red_escuela.backend.evaluation.entity.SectionGradeItemPublication;
import com.red_escuela.backend.evaluation.exception.InvalidGradeCorrectionReasonException;
import com.red_escuela.backend.evaluation.repository.GradeItemAuditRepository;
import com.red_escuela.backend.shared.domain.Grade;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class FinalGradeRevertAuditor {

    private final GradeItemAuditRepository auditRepository;
    private final Clock clock;

    public FinalGradeRevertAuditor(GradeItemAuditRepository auditRepository, Clock clock) {
        this.auditRepository = auditRepository;
        this.clock = clock;
    }

    public void recordRevert(GradeItem existing, Grade newScore, String reason, SectionKind kind, Long sectionId,
                             Long enrollmentId, Long actorPersonId) {
        String normalizedReason = normalizedReason(reason);
        auditRepository.save(GradeItemAudit.builder()
                .gradeItemId(existing.getId())
                .sectionKind(kind)
                .sectionId(sectionId)
                .enrollmentId(enrollmentId)
                .gradeItemType(existing.getType())
                .sequenceNumber(SectionGradeItemPublication.sequenceKey(existing.getSequenceNumber()))
                .previousScore(existing.getScore())
                .newScore(newScore)
                .reason(normalizedReason)
                .changedById(actorPersonId)
                .changedAt(LocalDateTime.now(clock))
                .kind(GradeItemAuditKind.FINAL_REVERT)
                .build());
    }

    private String normalizedReason(String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.length() < PublishedGradeCorrector.MIN_REASON_LENGTH
                || trimmed.length() > PublishedGradeCorrector.MAX_REASON_LENGTH) {
            throw new InvalidGradeCorrectionReasonException(
                    PublishedGradeCorrector.MIN_REASON_LENGTH, PublishedGradeCorrector.MAX_REASON_LENGTH);
        }
        return trimmed;
    }
}
