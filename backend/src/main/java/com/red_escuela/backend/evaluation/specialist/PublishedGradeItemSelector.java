package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.entity.SectionGradeItemPublication;
import com.red_escuela.backend.evaluation.repository.SectionGradeItemPublicationRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PublishedGradeItemSelector {

    private final SectionGradeItemPublicationRepository publicationRepository;

    public PublishedGradeItemSelector(SectionGradeItemPublicationRepository publicationRepository) {
        this.publicationRepository = publicationRepository;
    }

    public List<GradeItem> selectPublished(SectionKind kind, Long sectionId, List<GradeItem> items) {
        if (items.isEmpty()) {
            return items;
        }
        return filterPublished(items, publishedSlotsOf(kind, sectionId));
    }

    public Map<Long, List<GradeItem>> selectPublishedByEnrollment(
            SectionKind kind, Long sectionId, Map<Long, List<GradeItem>> itemsByEnrollment) {
        Set<String> publishedSlots = publishedSlotsOf(kind, sectionId);
        Map<Long, List<GradeItem>> published = new HashMap<>();
        itemsByEnrollment.forEach((enrollmentId, items) -> published.put(enrollmentId, filterPublished(items, publishedSlots)));
        return published;
    }

    private Set<String> publishedSlotsOf(SectionKind kind, Long sectionId) {
        return publicationRepository.findBySectionKindAndSectionId(kind, sectionId).stream()
                .map(publication -> slotOf(publication.getGradeItemType(), publication.getSequenceNumber()))
                .collect(Collectors.toSet());
    }

    private List<GradeItem> filterPublished(List<GradeItem> items, Set<String> publishedSlots) {
        return items.stream()
                .filter(item -> item.getType() == GradeItemType.FINAL
                        || publishedSlots.contains(slotOf(
                                item.getType(), SectionGradeItemPublication.sequenceKey(item.getSequenceNumber()))))
                .toList();
    }

    private static String slotOf(GradeItemType type, int sequenceNumber) {
        return type + "#" + sequenceNumber;
    }
}
