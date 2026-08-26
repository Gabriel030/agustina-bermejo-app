package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/*
 * CourseSubjectSection: comisión de una materia/módulo de un curso (CourseSubject) en un Term.
 * Incluye datos operativos: instructor, cupo, horario y esquema de evaluación.
 * Nota: se reutiliza la misma tabla 'course_section' para evitar migraciones por ahora.
 */
@Getter
@Setter
@Entity
@Table(name = "course_section")
public class CourseSubjectSection {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_subject_id", nullable = false)
    private CourseSubject courseSubject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    private Term term;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluation_scheme_id")
    private EvaluationScheme evaluationScheme;

    @Column(name = "schedule")
    private String schedule;

    @Column(name = "capacity")
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusType status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "section", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourseSubjectSectionInstructor> instructors;
}
