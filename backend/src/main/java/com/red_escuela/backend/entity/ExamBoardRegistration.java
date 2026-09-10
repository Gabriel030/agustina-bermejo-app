package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;

/*
 * Inscripción de un alumno a una mesa de examen: referencia la fila de inscripción puntual
 * (SubjectSectionEnrollment/CourseSubjectSectionEnrollment), no solo el Student — mismo
 * criterio de scope-por-carrera que las correlatividades de Inscripciones académicas. La
 * nota final vive únicamente en GradeItem (tipo FINAL): esta entidad solo trackea el ciclo
 * de vida de la anotación (status), nunca duplica el puntaje.
 */
@Getter
@Setter
@Entity
@Table(name = "exam_board_registration")
public class ExamBoardRegistration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_board_id", nullable = false)
    private ExamBoard examBoard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_section_enrollment_id")
    private SubjectSectionEnrollment programSubjectSectionEnrollment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_section_enrollment_id")
    private CourseSubjectSectionEnrollment courseSubjectSectionEnrollment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ExamBoardRegistrationStatus status;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @PrePersist
    @PreUpdate
    private void validateSingleEnrollmentTarget() {
        boolean hasProgram = this.programSubjectSectionEnrollment != null;
        boolean hasCourse = this.courseSubjectSectionEnrollment != null;
        if (hasProgram == hasCourse) {
            throw new IllegalStateException(
                    "ExamBoardRegistration must reference exactly one enrollment: program OR course.");
        }
    }
}
