package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Comisión de un Subject en un Term. A diferencia de CourseSubjectSection (módulo de
 * un Course, que no se comparte entre cursos), esta comisión no pertenece a una carrera
 * puntual: un Subject puede estar en el plan de varias Program distintas (ver
 * ProgramSubject) y una única SubjectSection admite alumnos de cualquiera de ellas.
 * Antes se llamaba ProgramSubjectSection y apuntaba a ProgramSubject — se renombró y
 * repuntó a Subject directamente al decidir que las materias se comparten entre
 * carreras (Spec Estructura académica, decisión 3). Se reutiliza la tabla física
 * "program_section" para no sumar una migración de rename sin necesidad real.
 */
@Getter
@Setter
@Entity
@Table(name = "program_section")
public class SubjectSection {
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
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

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
    private List<SubjectSectionInstructor> instructors;
}
