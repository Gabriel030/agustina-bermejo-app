package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.entity.SectionGradeItemPublication;
import com.red_escuela.backend.evaluation.exception.GradeItemPublishedLockedException;
import com.red_escuela.backend.evaluation.repository.SectionGradeItemPublicationRepository;
import org.springframework.stereotype.Component;

@Component
public class GradeItemPublicationLock {

    private final SectionGradeItemPublicationRepository publicationRepository;

    public GradeItemPublicationLock(SectionGradeItemPublicationRepository publicationRepository) {
        this.publicationRepository = publicationRepository;
    }

    public void requireUnlocked(SectionKind kind, Long sectionId, GradeItemType type, Integer sequenceNumber) {
        if (type == GradeItemType.FINAL) {
            return;
        }
        if (publicationRepository.existsBySectionKindAndSectionIdAndGradeItemTypeAndSequenceNumber(
                kind, sectionId, type, SectionGradeItemPublication.sequenceKey(sequenceNumber))) {
            throw new GradeItemPublishedLockedException(type.name(), sequenceNumber);
        }
    }
}
