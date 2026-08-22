package com.red_escuela.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "course_enrollment")
public class CourseEnrollment {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private CourseEnrollmentStatus status;

    @Column(name = "enrollment_date")
    private LocalDateTime enrollmentDate;

    @Column(name = "completion_date")
    private LocalDateTime completionDate;

    @OneToMany(mappedBy = "courseEnrollment", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = false)
    private List<CourseSubjectSectionEnrollment> sectionEnrollments;
}
