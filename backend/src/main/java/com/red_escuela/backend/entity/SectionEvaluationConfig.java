package com.red_escuela.backend.entity;
import com.red_escuela.backend.identity.entity.Person;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;

@Getter
@Setter
@Entity
@Table(name = "section_evaluation_config")
public class SectionEvaluationConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_section_id", nullable = false, unique = true)
    private SubjectSection subjectSection;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SectionEvaluationConfigStatus status;

    @Column(name = "tp_count")
    private Integer tpCount;

    @Column(name = "tp1_due_date")
    private LocalDate tp1DueDate;

    @Column(name = "tp2_due_date")
    private LocalDate tp2DueDate;

    @Column(name = "tp3_due_date")
    private LocalDate tp3DueDate;

    @Column(name = "exam1_date")
    private LocalDate exam1Date;

    @Column(name = "exam2_date")
    private LocalDate exam2Date;

    @Column(name = "exam3_date")
    private LocalDate exam3Date;

    @Column(name = "exam4_date")
    private LocalDate exam4Date;

    @Column(name = "final_call1_date")
    private LocalDate finalCall1Date;

    @Column(name = "final_call2_date")
    private LocalDate finalCall2Date;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private Person reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
}
