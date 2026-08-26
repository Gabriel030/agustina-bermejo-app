package com.red_escuela.backend.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "course")
public class Course {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @OneToMany(mappedBy = "course", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourseSubject> courseSubjectList;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    private Term term;

    @Column(name = "schedule")
    private String schedule;

    @Column(name = "capacity")
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusType isActive;

    @Column(name = "registration_start")
    private LocalDateTime registrationStart;

    @Column(name = "registration_end")
    private LocalDateTime registrationEnd;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "duration_months")
    private Integer durationMonths;
}

