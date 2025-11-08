package com.red_escuela.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "course_instructor")
public class CourseInstructor {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instructor_id")
    private Instructor instructor;

    @Column(name = "assigned_date")
    private LocalDateTime assignedDate;

    @Column(name = "is_primary")
    private Boolean isPrimary;

}
