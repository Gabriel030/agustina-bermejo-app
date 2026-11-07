package com.red_escuela.backend.evaluation.api;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.entity.SectionGradeItemPublication;
import com.red_escuela.backend.evaluation.entity.GradeItemAuditKind;
import com.red_escuela.backend.evaluation.repository.GradeItemAuditRepository;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import com.red_escuela.backend.evaluation.repository.SectionGradeItemPublicationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class GradeItemFinder {

    private final GradeItemRepository gradeItemRepository;
    private final SectionGradeItemPublicationRepository publicationRepository;
    private final GradeItemAuditRepository auditRepository;

    public GradeItemFinder(GradeItemRepository gradeItemRepository,
                           SectionGradeItemPublicationRepository publicationRepository,
                           GradeItemAuditRepository auditRepository) {
        this.gradeItemRepository = gradeItemRepository;
        this.publicationRepository = publicationRepository;
        this.auditRepository = auditRepository;
    }

    public List<GradeItemView> findPublishedByProgramEnrollment(Long enrollmentId) {
        List<GradeItem> items = gradeItemRepository.findByProgramSubjectSectionEnrollmentId(enrollmentId);
        if (items.isEmpty()) {
            return List.of();
        }
        Long sectionId = items.get(0).getProgramSubjectSectionEnrollment().getSubjectSection().getId();
        return publishedViews(items, SectionKind.PROGRAM, sectionId);
    }

    public List<GradeItemView> findPublishedByCourseEnrollment(Long enrollmentId) {
        List<GradeItem> items = gradeItemRepository.findByCourseSubjectSectionEnrollmentId(enrollmentId);
        if (items.isEmpty()) {
            return List.of();
        }
        Long sectionId = items.get(0).getCourseSubjectSectionEnrollment().getCourseSubjectSection().getId();
        return publishedViews(items, SectionKind.COURSE, sectionId);
    }

    private List<GradeItemView> publishedViews(List<GradeItem> items, SectionKind kind, Long sectionId) {
        Set<String> publishedSlots = publicationRepository.findBySectionKindAndSectionId(kind, sectionId).stream()
                .map(publication -> slotKey(publication.getGradeItemType(), publication.getSequenceNumber()))
                .collect(Collectors.toSet());
        List<GradeItem> published = items.stream()
                .filter(item -> item.getType() == GradeItemType.FINAL
                        || publishedSlots.contains(slotKey(item.getType(), SectionGradeItemPublication.sequenceKey(item.getSequenceNumber()))))
                .toList();
        Map<Long, GradeItemCorrectionView> lastCorrections = lastCorrectionsOf(published);
        return published.stream()
                .map(item -> viewOf(item, lastCorrections.get(item.getId())))
                .toList();
    }

    private Map<Long, GradeItemCorrectionView> lastCorrectionsOf(List<GradeItem> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        List<Long> itemIds = items.stream().map(GradeItem::getId).toList();
        Map<Long, GradeItemCorrectionView> lastCorrections = new HashMap<>();
        auditRepository.findByGradeItemIdInAndKindOrderByChangedAtDescIdDesc(itemIds, GradeItemAuditKind.CORRECTION)
                .forEach(audit -> lastCorrections.putIfAbsent(audit.getGradeItemId(),
                        new GradeItemCorrectionView(audit.getPreviousScore(), audit.getReason(), audit.getChangedAt())));
        return lastCorrections;
    }

    private static String slotKey(GradeItemType type, int sequenceNumber) {
        return type + ":" + sequenceNumber;
    }

    private static GradeItemView viewOf(GradeItem gradeItem, GradeItemCorrectionView correction) {
        Long enrollmentId = gradeItem.getProgramSubjectSectionEnrollment() != null
                ? gradeItem.getProgramSubjectSectionEnrollment().getId()
                : gradeItem.getCourseSubjectSectionEnrollment().getId();
        return new GradeItemView(
                gradeItem.getId(),
                enrollmentId,
                gradeItem.getType().name(),
                gradeItem.getSequenceNumber(),
                gradeItem.getScore(),
                gradeItem.getDate(),
                gradeItem.getExamBoard() == null ? null : gradeItem.getExamBoard().getId(),
                correction);
    }
}
