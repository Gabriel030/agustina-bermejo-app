package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;


/*
* Qué representa:
        Un rango de fechas con identidad propia: “2025 1er cuatrimestre”,
        *  “2025 2do cuatrimestre”, “2025 invierno”, etc.
        Se usa para agrupar todas las secciones que se ofrecen en ese período
        * y para consultar inscripciones/notas por período.

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
public class ProgramSubjectSectionEnrollment {
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

    // Spec Estructura académica: ProgramSubjectSection se renombró a SubjectSection y
    // pasó a apuntar a Subject en vez de ProgramSubject (una comisión ahora se comparte
    // entre carreras). Cambio mecánico para que compile — la lógica de esta entidad
    // (inscripción de un alumno a una comisión) es de la futura spec de Inscripciones.
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
