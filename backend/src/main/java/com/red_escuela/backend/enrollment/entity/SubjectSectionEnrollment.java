package com.red_escuela.backend.enrollment.entity;

import org.springframework.modulith.NamedInterface;

import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.identity.entity.Student;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.evaluation.entity.FinalGradeType;

@Getter
@Setter
@Entity
@Table(
    name = "subject_section_enrollment",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_prog_sec_enr_student_section",
            columnNames = {"student_id", "program_section_id"}
        )
    }
)
@NamedInterface("model")
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
    private Grade finalScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_grade_type")
    private FinalGradeType finalGradeType;
}
