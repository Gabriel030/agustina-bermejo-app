package com.red_escuela.backend.academicstructure.entity;

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
    name = "program_subject_corequisite",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_prog_coreq_subject_coreq",
            columnNames = {"program_subject_id", "corequisite_program_subject_id"}
        )
    }
)
@Check(constraints = "program_subject_id <> corequisite_program_subject_id")
public class ProgramSubjectCorequisite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_subject_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ProgramSubject programSubject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "corequisite_program_subject_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ProgramSubject corequisiteProgramSubject;
}
