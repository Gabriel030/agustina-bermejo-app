package com.red_escuela.backend.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "course_subject")
public class CourseSubject {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(name = "name")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name= "subject_recurrence")
    private SubjectRecurrence subjectRecurrence;

    @Column(name = "curricular_year")
    private Integer curricularYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "curricular_term")
    private CurricularTerm curricularTerm;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "weekly_workload")
    private Integer weeklyWorkload;

    @Column(name = "status")
    private Boolean status;

    @Column(name = "qualification_type")
    private String qualificationType;

    // Prerrequisitos (AND): módulos requeridos para poder cursar este CourseSubject
    @OneToMany(mappedBy = "courseSubject", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourseSubjectPrerequisite> prerequisites;

    // Módulos que dependen de este CourseSubject (referencias inversas)
    @OneToMany(mappedBy = "prerequisiteCourseSubject", fetch = FetchType.LAZY)
    private List<CourseSubjectPrerequisite> requiredBy;

}

