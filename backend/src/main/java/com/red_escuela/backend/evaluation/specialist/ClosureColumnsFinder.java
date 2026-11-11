package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.dto.ClosureSheetColumnResponse;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.repository.SectionGradeItemPublicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ClosureColumnsFinder {

    private final SectionGradeItemPublicationRepository publicationRepository;
    private final SectionFinder sectionFinder;

    public ClosureColumnsFinder(SectionGradeItemPublicationRepository publicationRepository, SectionFinder sectionFinder) {
        this.publicationRepository = publicationRepository;
        this.sectionFinder = sectionFinder;
    }

    @Transactional(readOnly = true)
    public List<ClosureSheetColumnResponse> findColumns(SectionKind kind, Long sectionId) {
        EvaluationScheme scheme = kind == SectionKind.PROGRAM
                ? sectionFinder.findProgramSectionEntity(sectionId).getEvaluationScheme()
                : sectionFinder.findCourseSectionEntity(sectionId).getEvaluationScheme();
        if (scheme == null) {
            return List.of();
        }
        Set<String> publishedSlots = publicationRepository.findBySectionKindAndSectionId(kind, sectionId).stream()
                .map(publication -> slotKey(publication.getGradeItemType(), publication.getSequenceNumber()))
                .collect(Collectors.toSet());
        List<ClosureSheetColumnResponse> columns = new ArrayList<>();
        appendColumns(columns, GradeItemType.EXAM, scheme.getExamCountExpected(), publishedSlots);
        appendColumns(columns, GradeItemType.TP, scheme.getTpCountExpected(), publishedSlots);
        return columns;
    }

    public boolean allPublished(List<ClosureSheetColumnResponse> columns) {
        return !columns.isEmpty() && columns.stream().allMatch(ClosureSheetColumnResponse::published);
    }

    public boolean hasAnyPublished(SectionKind kind, Long sectionId) {
        return !publicationRepository.findBySectionKindAndSectionId(kind, sectionId).isEmpty();
    }

    private void appendColumns(List<ClosureSheetColumnResponse> columns, GradeItemType type, Integer expected,
                               Set<String> publishedSlots) {
        int count = expected == null ? 0 : expected;
        for (int sequence = 1; sequence <= count; sequence++) {
            columns.add(new ClosureSheetColumnResponse(type, sequence, publishedSlots.contains(slotKey(type, sequence))));
        }
    }

    private String slotKey(GradeItemType type, int sequenceNumber) {
        return type + ":" + sequenceNumber;
    }
}
