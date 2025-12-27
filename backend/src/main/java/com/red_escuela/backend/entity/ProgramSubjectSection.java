package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/*
 * ProgramSubjectSection: comisión de una materia de un programa (ProgramSubject) en un Term.
 * Incluye datos operativos: instructor, cupo, horario y esquema de evaluación.
 * Nota: se reutiliza la misma tabla 'program_section' para evitar migraciones por ahora.
 */
@Getter
@Setter
@Entity
@Table(name = "program_section")
public class ProgramSubjectSection {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_subject_id", nullable = false)
    private ProgramSubject programSubject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    private Term term;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instructor_id")
    private Instructor instructor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluation_scheme_id")
    private EvaluationScheme evaluationScheme;

    @Column(name = "schedule")
    private String schedule;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "status")
    private Boolean status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "section", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProgramSubjectSectionInstructor> instructors;
}
