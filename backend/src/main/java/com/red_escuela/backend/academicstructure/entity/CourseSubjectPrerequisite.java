package com.red_escuela.backend.academicstructure.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@Entity
@Table(
    name = "course_subject_prerequisite",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_course_prereq_target_req",
            columnNames = {"course_subject_id", "prerequisite_course_subject_id", "requirement_type"}
        )
    }
)
@Check(constraints = "min_grade IS NULL OR (min_grade >= 0 AND min_grade <= 10)")
public class CourseSubjectPrerequisite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Materia de curso objetivo
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_subject_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CourseSubject courseSubject;

    // Materia de curso requerida
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prerequisite_course_subject_id", nullable = false)
    private CourseSubject prerequisiteCourseSubject;

    @Enumerated(EnumType.STRING)
    @Column(name = "requirement_type", nullable = false)
    private RequirementType requirementType; // APPROVED o PASSED_COURSEWORK

    @Column(name = "min_grade")
    private Double minGrade; // opcional

    @Column(name = "notes", length = 500)
    private String notes; // opcional
}
