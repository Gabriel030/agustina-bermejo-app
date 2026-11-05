package com.red_escuela.backend.academicstructure.entity;

import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.shared.domain.Grade;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@Entity
@Table(
    name = "program_subject_prerequisite",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_prog_prereq_target_req",
            columnNames = {"program_subject_id", "prerequisite_program_subject_id", "requirement_type"}
        )
    }
)
@Check(constraints = "min_grade IS NULL OR (min_grade >= 0 AND min_grade <= 10)")
public class ProgramSubjectPrerequisite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_subject_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ProgramSubject programSubject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prerequisite_program_subject_id", nullable = false)
    private ProgramSubject prerequisiteProgramSubject;

    @Enumerated(EnumType.STRING)
    @Column(name = "requirement_type", nullable = false)
    private RequirementType requirementType;

    @Column(name = "min_grade")
    private Grade minGrade;

    @Column(name = "notes", length = 500)
    private String notes;
}
