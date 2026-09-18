package com.red_escuela.backend.academicstructure.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.red_escuela.backend.entity.StatusType;

@Getter
@Setter
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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusType status = StatusType.ACTIVE;

    @Column(name = "max_final_exam_attempts")
    private Integer maxFinalExamAttempts;
}
