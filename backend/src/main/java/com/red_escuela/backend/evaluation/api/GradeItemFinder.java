package com.red_escuela.backend.evaluation.api;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.entity.SectionGradeItemPublication;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import com.red_escuela.backend.evaluation.repository.SectionGradeItemPublicationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class GradeItemFinder {

    private final GradeItemRepository gradeItemRepository;
    private final SectionGradeItemPublicationRepository publicationRepository;

    public GradeItemFinder(GradeItemRepository gradeItemRepository,
                           SectionGradeItemPublicationRepository publicationRepository) {
        this.gradeItemRepository = gradeItemRepository;
        this.publicationRepository = publicationRepository;
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
        return items.stream()
                .filter(item -> item.getType() == GradeItemType.FINAL
                        || publishedSlots.contains(slotKey(item.getType(), SectionGradeItemPublication.sequenceKey(item.getSequenceNumber()))))
                .map(GradeItemFinder::viewOf)
                .toList();
    }

    private static String slotKey(GradeItemType type, int sequenceNumber) {
        return type + ":" + sequenceNumber;
    }

    private static GradeItemView viewOf(GradeItem gradeItem) {
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
                gradeItem.getExamBoard() == null ? null : gradeItem.getExamBoard().getId());
    }
}
