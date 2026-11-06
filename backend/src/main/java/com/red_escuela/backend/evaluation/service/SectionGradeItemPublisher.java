package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.api.SectionTeachingFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.dto.PublishedGradesSummary;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.entity.SectionGradeItemPublication;
import com.red_escuela.backend.evaluation.exception.GradeItemTypeNotPublishableException;
import com.red_escuela.backend.evaluation.exception.NoPendingGradesToPublishException;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import com.red_escuela.backend.evaluation.repository.SectionGradeItemPublicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class SectionGradeItemPublisher {

    private final SectionFinder sectionFinder;
    private final SectionTeachingFinder sectionTeachingFinder;
    private final GradeItemRepository gradeItemRepository;
    private final SectionGradeItemPublicationRepository publicationRepository;
    private final Clock clock;

    public SectionGradeItemPublisher(SectionFinder sectionFinder,
                                     SectionTeachingFinder sectionTeachingFinder,
                                     GradeItemRepository gradeItemRepository,
                                     SectionGradeItemPublicationRepository publicationRepository,
                                     Clock clock) {
        this.sectionFinder = sectionFinder;
        this.sectionTeachingFinder = sectionTeachingFinder;
        this.gradeItemRepository = gradeItemRepository;
        this.publicationRepository = publicationRepository;
        this.clock = clock;
    }

    @Transactional
    public PublishedGradesSummary publish(
            SectionKind kind, Long sectionId, Long instructorId, Long publisherPersonId,
            GradeItemType type, Integer sequenceNumber) {
        if (type == GradeItemType.FINAL) {
            throw new GradeItemTypeNotPublishableException(type);
        }
        sectionFinder.requireExists(kind, sectionId);
        sectionTeachingFinder.requireTeaches(kind, sectionId, instructorId);
        if (publicationRepository.existsBySectionKindAndSectionIdAndGradeItemTypeAndSequenceNumber(
                kind, sectionId, type, sequenceNumber)) {
            throw new NoPendingGradesToPublishException(sectionId, type, sequenceNumber);
        }
        int gradeCount = (int) countGrades(kind, sectionId, type, sequenceNumber);
        if (gradeCount == 0) {
            throw new NoPendingGradesToPublishException(sectionId, type, sequenceNumber);
        }
        int studentCount = (int) countEnrollments(kind, sectionId, type, sequenceNumber);
        publicationRepository.save(SectionGradeItemPublication.of(
                kind, sectionId, type, sequenceNumber, LocalDateTime.now(clock), publisherPersonId));
        return new PublishedGradesSummary(gradeCount, studentCount);
    }

    private long countGrades(SectionKind kind, Long sectionId, GradeItemType type, Integer sequenceNumber) {
        return switch (kind) {
            case PROGRAM -> gradeItemRepository.countByProgramSection(sectionId, type, sequenceNumber);
            case COURSE -> gradeItemRepository.countByCourseSection(sectionId, type, sequenceNumber);
        };
    }

    private long countEnrollments(SectionKind kind, Long sectionId, GradeItemType type, Integer sequenceNumber) {
        return switch (kind) {
            case PROGRAM -> gradeItemRepository.countEnrollmentsByProgramSection(sectionId, type, sequenceNumber);
            case COURSE -> gradeItemRepository.countEnrollmentsByCourseSection(sectionId, type, sequenceNumber);
        };
    }
}
