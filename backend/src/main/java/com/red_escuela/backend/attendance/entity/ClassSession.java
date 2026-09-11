package com.red_escuela.backend.attendance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Instructor;

/*
 * "Clase" puntual de una comisión, en una fecha. Existe para colgar temario/observaciones/
 * cancelación de esa fecha, sin depender de un concepto de horario o aula estructurados (esos
 * no existen todavía en el dominio — ver Épica 4 del roadmap frontend). classroom/startTime/
 * endTime quedan nullable y sin uso por ahora, reservados para cuando esa épica los necesite.
 */
@Getter
@Setter
@Entity
@Table(name = "class_session")
@Check(constraints = "(program_section_id IS NOT NULL AND course_section_id IS NULL) "
        + "OR (program_section_id IS NULL AND course_section_id IS NOT NULL)")
public class ClassSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_section_id")
    private SubjectSection subjectSection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_section_id")
    private CourseSubjectSection courseSubjectSection;

    @Column(name = "class_date", nullable = false)
    private LocalDate classDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ClassSessionStatus status;

    @Column(name = "topic", length = 1000)
    private String topic;

    @Column(name = "observations", length = 500)
    private String observations;

    @Column(name = "classroom")
    private String classroom;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private Instructor createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        validateSingleSectionTarget();
    }

    @PreUpdate
    private void validateSingleSectionTarget() {
        boolean hasProgram = this.subjectSection != null;
        boolean hasCourse = this.courseSubjectSection != null;
        if (hasProgram == hasCourse) {
            throw new IllegalStateException("ClassSession must reference exactly one section: program OR course.");
        }
    }
}
