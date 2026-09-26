package com.red_escuela.backend.academicstructure.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "subject")
public class Subject {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "code", nullable = false, unique = true, length = 4)
    private String code;

    @Column(name = "description")
    private String description;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SubjectStatus status = SubjectStatus.DRAFT;

    @Column(name = "max_final_exam_attempts")
    private Integer maxFinalExamAttempts;

    @Column(name = "weekly_hours")
    private Integer weeklyHours;

    @Column(name = "weekly_hours_lab")
    private Integer weeklyHoursLab;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade_mode")
    private GradeMode gradeMode;
}
