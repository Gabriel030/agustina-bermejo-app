package com.red_escuela.backend.evaluation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.shared.domain.Grade;

@Getter
@Setter
@Entity
@Table(name = "grade_item")
@Check(constraints = "((course_section_enrollment_id IS NOT NULL AND program_section_enrollment_id IS NULL) "
        + "OR (course_section_enrollment_id IS NULL AND program_section_enrollment_id IS NOT NULL)) "
        + "AND (weight IS NULL OR (weight >= 0 AND weight <= 1))")
public class GradeItem {
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

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private GradeItemType type;

    @Column(name = "sequence_number")
    private Integer sequenceNumber;

    @Column(name = "weight")
    private Double weight;

    @Column(name = "date")
    private LocalDateTime date;

    @Column(name = "score")
    private Grade score;

    @Column(name = "comments")
    private String comments;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_board_id")
    private ExamBoard examBoard;

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
