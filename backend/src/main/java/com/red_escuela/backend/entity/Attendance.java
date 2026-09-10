package com.red_escuela.backend.entity;
import com.red_escuela.backend.identity.entity.Person;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.red_escuela.backend.academicstructure.entity.Instructor;

/*
 * Asistencia de un alumno a una clase puntual (por fecha), atada a la fila de inscripción
 * específica (no a (student, section) suelto) — mismo criterio que GradeItem: si un alumno
 * recursa (attemptNumber distinto), la inscripción ya identifica intento+comisión+alumno sin
 * ambigüedad. XOR course/program con el mismo patrón @PrePersist/@PreUpdate de GradeItem.
 */
@Getter
@Setter
@Entity
@Table(name = "attendance")
public class Attendance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_section_enrollment_id")
    private CourseSubjectSectionEnrollment courseSubjectSectionEnrollment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_section_enrollment_id")
    private SubjectSectionEnrollment programSubjectSectionEnrollment;

    @Column(name = "class_date", nullable = false)
    private LocalDate classDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AttendanceStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_id", nullable = false)
    private Instructor recordedBy;

    @Column(name = "justification_reason", length = 500)
    private String justificationReason;

    @Column(name = "justification_attachment_url")
    private String justificationAttachmentUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "justified_by_id")
    private Person justifiedBy;

    @Column(name = "justified_at")
    private LocalDateTime justifiedAt;

    @PrePersist
    @PreUpdate
    private void validateSingleEnrollmentTarget() {
        boolean hasCourse = this.courseSubjectSectionEnrollment != null;
        boolean hasProgram = this.programSubjectSectionEnrollment != null;
        if (hasCourse == hasProgram) {
            throw new IllegalStateException("Attendance must reference exactly one enrollment: course OR program.");
        }
    }
}
