package com.red_escuela.backend.evaluation.entity;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.shared.domain.Grade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "grade_item_audit")
public class GradeItemAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "grade_item_id")
    private Long gradeItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "section_kind", nullable = false)
    private SectionKind sectionKind;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Column(name = "enrollment_id", nullable = false)
    private Long enrollmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade_item_type", nullable = false)
    private GradeItemType gradeItemType;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "previous_score")
    private Grade previousScore;

    @Column(name = "new_score")
    private Grade newScore;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "changed_by_id")
    private Long changedById;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private GradeItemAuditKind kind;
}
