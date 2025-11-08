package com.red_escuela.backend.entity;
import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "subject")
public class Subject {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(name = "name")
    private String name;

    @Column(name= "subject_type")
    private String subjectType;

    @Column(name = "weekly_workload")
    private Integer weeklyWorkload;

    @Column(name = "duration_years", nullable = false)
    private Integer durationYears;

    @Column(name = "status")
    private Boolean status;

    @Column(name = "qualification_type")
    private String qualificationType;

    @Column(name = "first_exam_approved")
    private Boolean firstExamApproved;

    @Column(name = "recoveryExamApproved")
    private Boolean recoveryExamApproved;

    @Column(name = "final_exam_approved")
    private Boolean finalExamApproved;
}
