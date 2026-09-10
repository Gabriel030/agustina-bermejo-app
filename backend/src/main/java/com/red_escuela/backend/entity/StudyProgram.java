package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.red_escuela.backend.academicstructure.entity.Program;

@Getter
@Setter
@Entity
@Table(name = "study_program")
public class StudyProgram {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "description")
    private String description;

    @Column(name = "grade")
    private String grade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id")
    private Program program;

    @Column(name = "year_number")
    private Integer year;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusType status;

}
