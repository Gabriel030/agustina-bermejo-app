package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/*
 * Inscripción de un alumno a una comisión (SubjectSection) de una materia de carrera,
 * dentro del contexto de una ProgramEnrollment puntual (misma materia, dos carreras
 * distintas => dos filas distintas, una por cada ProgramEnrollment).
 *
 * Renombrada desde ProgramSubjectSectionEnrollment (spec Inscripciones académicas):
 * la tabla física sigue siendo program_section_enrollment y la columna
 * program_section_id — no se tocó nada a nivel de base, solo el nombre de la clase,
 * que ya no tenía sentido tras el rename de ProgramSubjectSection -> SubjectSection.
 */
@Getter
@Setter
@Entity
@Table(
    name = "program_section_enrollment",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_prog_sec_enr_student_section",
            columnNames = {"student_id", "program_section_id"}
        )
    }
)
public class SubjectSectionEnrollment {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_enrollment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ProgramEnrollment programEnrollment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_section_id", nullable = false)
    private SubjectSection subjectSection;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private EnrollmentStatus status;

    @Column(name = "enrolled_at")
    private LocalDateTime enrolledAt;

    @Column(name = "dropped_at")
    private LocalDateTime droppedAt;

    @Column(name = "attempt_number")
    private Integer attemptNumber;

    @Column(name = "final_score")
    private Double finalScore;

    @Column(name = "final_grade_type")
    private String finalGradeType;
}
