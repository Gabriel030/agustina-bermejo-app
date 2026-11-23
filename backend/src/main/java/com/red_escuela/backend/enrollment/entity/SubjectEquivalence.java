package com.red_escuela.backend.enrollment.entity;

import com.red_escuela.backend.shared.domain.Grade;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "subject_equivalence")
public class SubjectEquivalence {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_enrollment_id", nullable = false)
    private ProgramEnrollment programEnrollment;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "student_document_id")
    private Long studentDocumentId;

    @Column(name = "origin_institution", nullable = false)
    private String originInstitution;

    @Column(name = "origin_subject_name", nullable = false)
    private String originSubjectName;

    @Column(name = "origin_score")
    private Grade originScore;

    @Column(name = "recognized_on", nullable = false)
    private LocalDate recognizedOn;

    @Column(name = "resolution_number", nullable = false, length = 100)
    private String resolutionNumber;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private SubjectEquivalenceStatus status = SubjectEquivalenceStatus.ACTIVE;

    @Column(name = "recorded_by_id", nullable = false)
    private Long recordedById;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "voided_by_id")
    private Long voidedById;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "void_reason", length = 500)
    private String voidReason;
}
