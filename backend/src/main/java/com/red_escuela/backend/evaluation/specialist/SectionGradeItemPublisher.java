package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.api.SectionTeachingFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.dto.PublishGradeItemRequest;
import com.red_escuela.backend.evaluation.dto.PublishedGradesSummary;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.entity.SectionGradeItemPublication;
import com.red_escuela.backend.evaluation.exception.GradeItemTypeNotPublishableException;
import com.red_escuela.backend.evaluation.events.GradesPublishedEvent;
import com.red_escuela.backend.evaluation.exception.NoPendingGradesToPublishException;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import com.red_escuela.backend.evaluation.repository.SectionGradeItemPublicationRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SectionGradeItemPublisher {

    private final SectionFinder sectionFinder;
    private final SectionTeachingFinder sectionTeachingFinder;
    private final GradeItemRepository gradeItemRepository;
    private final SectionGradeItemPublicationRepository publicationRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public SectionGradeItemPublisher(SectionFinder sectionFinder,
                                     SectionTeachingFinder sectionTeachingFinder,
                                     GradeItemRepository gradeItemRepository,
                                     SectionGradeItemPublicationRepository publicationRepository,
                                     Clock clock,
                                     ApplicationEventPublisher eventPublisher) {
        this.sectionFinder = sectionFinder;
        this.sectionTeachingFinder = sectionTeachingFinder;
        this.gradeItemRepository = gradeItemRepository;
        this.publicationRepository = publicationRepository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PublishedGradesSummary publishProgramSection(
            Long sectionId, Long instructorId, Long publisherPersonId, PublishGradeItemRequest request) {
        return publish(SectionKind.PROGRAM, sectionId, instructorId, publisherPersonId,
                request.gradeType(), request.sequenceNumber());
    }

    @Transactional
    public PublishedGradesSummary publishCourseSection(
            Long sectionId, Long instructorId, Long publisherPersonId, PublishGradeItemRequest request) {
        return publish(SectionKind.COURSE, sectionId, instructorId, publisherPersonId,
                request.gradeType(), request.sequenceNumber());
    }

    private PublishedGradesSummary publish(
            SectionKind kind, Long sectionId, Long instructorId, Long publisherPersonId,
            GradeItemType type, Integer sequenceNumber) {
        if (type == GradeItemType.FINAL) {
            throw new GradeItemTypeNotPublishableException(type.name());
        }
        sectionFinder.requireExists(kind, sectionId);
        sectionTeachingFinder.requireTeaches(kind, sectionId, instructorId);
        if (publicationRepository.existsBySectionKindAndSectionIdAndGradeItemTypeAndSequenceNumber(
                kind, sectionId, type, sequenceNumber)) {
            throw new NoPendingGradesToPublishException(sectionId, type.name(), sequenceNumber);
        }
        int gradeCount = (int) countGrades(kind, sectionId, type, sequenceNumber);
        if (gradeCount == 0) {
            throw new NoPendingGradesToPublishException(sectionId, type.name(), sequenceNumber);
        }
        int studentCount = (int) countEnrollments(kind, sectionId, type, sequenceNumber);
        publicationRepository.save(SectionGradeItemPublication.of(
                kind, sectionId, type, sequenceNumber, LocalDateTime.now(clock), publisherPersonId));
        eventPublisher.publishEvent(GradesPublishedEvent.publishedFor(
                sectionId, sectionNameOf(kind, sectionId), type.displayName(), sequenceNumber,
                studentPersonIdsOf(kind, sectionId, type, sequenceNumber)));
        return new PublishedGradesSummary(gradeCount, studentCount);
    }

    private String sectionNameOf(SectionKind kind, Long sectionId) {
        return switch (kind) {
            case PROGRAM -> sectionFinder.findProgramSection(sectionId).name();
            case COURSE -> sectionFinder.findCourseSection(sectionId).name();
        };
    }

    private List<Long> studentPersonIdsOf(SectionKind kind, Long sectionId, GradeItemType type, Integer sequenceNumber) {
        return switch (kind) {
            case PROGRAM -> gradeItemRepository.findStudentPersonIdsByProgramSection(sectionId, type, sequenceNumber);
            case COURSE -> gradeItemRepository.findStudentPersonIdsByCourseSection(sectionId, type, sequenceNumber);
        };
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
