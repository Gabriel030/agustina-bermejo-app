package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Every program opens exactly 2 cohorts per year (institutional policy) — each one
// administratively independent, with its own admission window and its own real cursada start.
@Getter
@Setter
@Entity
@Table(name = "program_cohort", uniqueConstraints = @UniqueConstraint(columnNames = {"template_id", "intake"}))
public class ProgramCohort {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private ProgramYearBillingTemplate template;

    @Enumerated(EnumType.STRING)
    @Column(name = "intake", nullable = false)
    private CohortIntake intake;

    // From when this cohort's enrollment fee can be paid.
    @Column(name = "admission_start", nullable = false)
    private LocalDate admissionStart;

    @Column(name = "admission_end", nullable = false)
    private LocalDate admissionEnd;

    // First real month of this cohort's cursada — never the month the enrollment was requested.
    @Column(name = "cursada_start", nullable = false)
    private LocalDate cursadaStart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private Person createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
