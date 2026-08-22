package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "grade_item")
@Check(constraints = "(course_section_enrollment_id IS NOT NULL AND program_section_enrollment_id IS NULL) "
        + "OR (course_section_enrollment_id IS NULL AND program_section_enrollment_id IS NOT NULL)")
public class GradeItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    // Enlaza a UNA inscripción de sección de curso o programa (no a ambas)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_section_enrollment_id")
    private CourseSubjectSectionEnrollment courseSubjectSectionEnrollment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_section_enrollment_id")
    private ProgramSubjectSectionEnrollment programSubjectSectionEnrollment;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private GradeItemType type; // EXAM, TP, FINAL

    // Para EXAM: número de parcial (1..N). Para TP también puede numerarse si aplica.
    @Column(name = "sequence_number")
    private Integer sequenceNumber;

    // Peso relativo para cálculo de nota final si corresponde (0..1)
    @Column(name = "weight")
    private Double weight;

    @Column(name = "date")
    private LocalDateTime date;

    @Column(name = "score")
    private Double score;

    @Column(name = "max_score")
    private Double maxScore;

    @Column(name = "comments")
    private String comments;

    @PrePersist
    @PreUpdate
    private void validateSingleEnrollmentTarget() {
        boolean hasCourse = this.courseSubjectSectionEnrollment != null;
        boolean hasProgram = this.programSubjectSectionEnrollment != null;
        if (hasCourse == hasProgram) {
            throw new IllegalStateException("GradeItem must reference exactly one enrollment: course OR program.");
        }
    }
}
