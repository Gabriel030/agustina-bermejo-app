package com.red_escuela.backend.entity;

import jakarta.persistence.*;

import java.util.List;

public class Student {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "persona_id")
    private Person person;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id")
    private List<Program> programList;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private List<Course> courseList;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id")
    private Institution institution;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private List<CourseEnrollment> courseEnrollmentList;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id")
    private List<ProgramEnrollment> programEnrollmentList;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "subjects")
    private List<Subject> subjects;


}
