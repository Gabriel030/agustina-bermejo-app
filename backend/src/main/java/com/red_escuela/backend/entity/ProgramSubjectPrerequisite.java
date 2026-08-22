package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
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
public class ProgramSubjectPrerequisite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Materia objetivo (a la que quiero inscribirme)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_subject_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ProgramSubject programSubject;

    // Materia requerida (prerrequisito)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prerequisite_program_subject_id", nullable = false)
    private ProgramSubject prerequisiteProgramSubject;

    @Enumerated(EnumType.STRING)
    @Column(name = "requirement_type", nullable = false)
    private RequirementType requirementType; // APPROVED o PASSED_COURSEWORK

    @Column(name = "min_grade")
    private Double minGrade; // opcional

    @Column(name = "notes", length = 500)
    private String notes; // opcional
}
