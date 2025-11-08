package com.red_escuela.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

public class CourseEnrollment {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(name = "status")
    private Boolean status;

    @Column(name = "enrollment_date")
    private LocalDateTime enrollmentDate;
}
