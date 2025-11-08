package com.red_escuela.backend.entity;
import jakarta.persistence.*;

@Entity
@Table(name = "subjetct")
public class Subject {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
    private String qualification_type;

    @Column(name = "first_exam_approved")
    private Boolean firstExamApproved;

    @Column(name = "recoveryExamApproved")
    private Boolean recoveryExamApproved;

    @Column(name = "final_exam_approved")
    private Boolean finalExamApproved;
}
