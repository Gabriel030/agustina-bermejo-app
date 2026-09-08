package com.red_escuela.backend.academicstructure.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import com.red_escuela.backend.entity.EvaluationScheme;
import com.red_escuela.backend.entity.StatusType;

/**
 * Comisión de un Subject en un Term. A diferencia de CourseSubjectSection (módulo de
 * un Course, que no se comparte entre cursos), esta comisión no pertenece a una carrera
 * puntual: un Subject puede estar en el plan de varias Program distintas (ver
 * ProgramSubject) y una única SubjectSection admite alumnos de cualquiera de ellas.
 * Antes se llamaba ProgramSubjectSection y apuntaba a ProgramSubject — se renombró y
 * repuntó a Subject directamente al decidir que las materias se comparten entre
 * carreras (Spec Estructura académica, decisión 3). La tabla física se llamó
 * "program_section" hasta V20, que la renombró a "subject_section" para que dejara de
 * desentonar con el nombre de la clase.
 */
@Getter
@Setter
@Entity
@Table(name = "subject_section")
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

    // Porcentaje máximo de ausentismo permitido; si es null, no se calcula "libre por
    // inasistencias" para esta comisión (nunca un campo persistido, siempre derivado).
    @Column(name = "max_absence_percentage")
    private Double maxAbsencePercentage;

    @OneToMany(mappedBy = "section", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SubjectSectionInstructor> instructors;
}
