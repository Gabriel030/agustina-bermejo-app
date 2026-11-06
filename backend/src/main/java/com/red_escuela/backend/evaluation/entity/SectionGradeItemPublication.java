package com.red_escuela.backend.evaluation.entity;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "section_grade_item_publication")
public class SectionGradeItemPublication {

    public static final int NO_SEQUENCE = -1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "section_kind", nullable = false)
    private SectionKind sectionKind;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade_item_type", nullable = false)
    private GradeItemType gradeItemType;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(name = "published_by_id")
    private Long publishedById;

    public static SectionGradeItemPublication of(
            SectionKind sectionKind, Long sectionId, GradeItemType gradeItemType, int sequenceNumber,
            LocalDateTime publishedAt, Long publishedById) {
        SectionGradeItemPublication publication = new SectionGradeItemPublication();
        publication.sectionKind = sectionKind;
        publication.sectionId = sectionId;
        publication.gradeItemType = gradeItemType;
        publication.sequenceNumber = sequenceNumber;
        publication.publishedAt = publishedAt;
        publication.publishedById = publishedById;
        return publication;
    }

    public static int sequenceKey(Integer sequenceNumber) {
        return sequenceNumber == null ? NO_SEQUENCE : sequenceNumber;
    }
}
