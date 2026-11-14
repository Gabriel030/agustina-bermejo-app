package com.red_escuela.backend.evaluation.api;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.enrollment.api.HistoricalEnrollmentRecorder;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.entity.SectionGradeItemPublication;
import com.red_escuela.backend.evaluation.exception.HistoricalGradeReferenceMissingException;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import com.red_escuela.backend.evaluation.repository.SectionGradeItemPublicationRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.shared.domain.Grade;
import jakarta.persistence.EntityManager;
import java.time.LocalTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HistoricalGradeItemRecorder {
    private final GradeItemRepository gradeItemRepository;
    private final SectionGradeItemPublicationRepository publicationRepository;
    private final HistoricalEnrollmentRecorder enrollmentRecorder;
    private final CurrentActorFinder currentActorFinder;
    private final EntityManager entityManager;

    public HistoricalGradeItemRecorder(GradeItemRepository gradeItemRepository,
            SectionGradeItemPublicationRepository publicationRepository, HistoricalEnrollmentRecorder enrollmentRecorder,
            CurrentActorFinder currentActorFinder, EntityManager entityManager) {
        this.gradeItemRepository = gradeItemRepository;
        this.publicationRepository = publicationRepository;
        this.enrollmentRecorder = enrollmentRecorder;
        this.currentActorFinder = currentActorFinder;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public boolean gradeItemExists(DocumentType type, String number, Long sectionId, String itemType, int sequenceNumber) {
        return enrollmentRecorder.findSectionEnrollmentId(type, number, sectionId)
                .flatMap(enrollmentId -> gradeItemRepository.findByProgramSubjectSectionEnrollmentIdAndTypeAndSequenceNumber(
                        enrollmentId, GradeItemType.valueOf(itemType), sequenceNumber))
                .isPresent();
    }

    @Transactional
    public Long recordGradeItem(HistoricalGradeItem historical) {
        Long enrollmentId = enrollmentRecorder
                .findSectionEnrollmentId(historical.documentType(), historical.documentNumber(), historical.sectionId())
                .orElseThrow(() -> new HistoricalGradeReferenceMissingException(historical.sectionId()));
        GradeItem item = new GradeItem();
        item.setProgramSubjectSectionEnrollment(entityManager.getReference(SubjectSectionEnrollment.class, enrollmentId));
        item.setType(GradeItemType.valueOf(historical.itemType()));
        item.setSequenceNumber(historical.sequenceNumber());
        item.setDate(historical.date().atTime(LocalTime.NOON));
        item.setScore(Grade.of(historical.score()));
        Long itemId = gradeItemRepository.save(item).getId();
        if (!publicationRepository.existsBySectionKindAndSectionIdAndGradeItemTypeAndSequenceNumber(SectionKind.PROGRAM,
                historical.sectionId(), GradeItemType.valueOf(historical.itemType()), historical.sequenceNumber())) {
            publicationRepository.save(SectionGradeItemPublication.of(SectionKind.PROGRAM, historical.sectionId(),
                    GradeItemType.valueOf(historical.itemType()), historical.sequenceNumber(), historical.date().atTime(LocalTime.NOON),
                    currentActorFinder.currentPersonId()));
        }
        return itemId;
    }

    @Transactional
    public void discardGradeItem(Long itemId) {
        gradeItemRepository.findById(itemId).ifPresent(item -> {
            Long sectionId = item.getProgramSubjectSectionEnrollment().getSubjectSection().getId();
            GradeItemType itemType = item.getType();
            Integer sequenceNumber = item.getSequenceNumber();
            gradeItemRepository.delete(item);
            gradeItemRepository.flush();
            if (gradeItemRepository.countEnrollmentsByProgramSection(sectionId, itemType, sequenceNumber) == 0) {
                publicationRepository.findBySectionKindAndSectionId(SectionKind.PROGRAM, sectionId).stream()
                        .filter(publication -> publication.getGradeItemType() == itemType
                                && publication.getSequenceNumber() == sequenceNumber)
                        .forEach(publicationRepository::delete);
                publicationRepository.flush();
            }
        });
    }
}
