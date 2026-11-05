package com.red_escuela.backend.academicstructure.entity;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "course_section_instructor")
public class CourseSubjectSectionInstructor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_section_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CourseSubjectSection section;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instructor_id", nullable = false)
    private Instructor instructor;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private InstructorRole role;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "hours_per_week")
    private Integer hoursPerWeek;

    @Column(name = "notes", length = 500)
    private String notes;
}
