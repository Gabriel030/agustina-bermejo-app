package com.red_escuela.backend.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.util.List;

@Setter
@Getter
@Entity
@Table(name = "program_subject")
public class ProgramSubject {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Program program;

    // El Subject se comparte entre carreras (Spec Estructura académica, decisión 3):
    // esta fila es "cómo ESTA carrera incluye ese Subject en su plan", no la materia en sí.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(name = "credits", nullable = false)
    private Integer credits = 0;

    // Null = obligatoria (tiene que estar APPROVED sí o sí para egresar, ver decisión 4).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "elective_group_id")
    private ElectiveGroup electiveGroup;

    @Column(name = "curricular_year")
    private Integer curricularYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "curricular_term")
    private CurricularTerm curricularTerm;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusType status;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_recurrence")
    private SubjectRecurrence subjectRecurrence;

    // Prerrequisitos (AND): materias requeridas para poder cursar esta ProgramSubject
    @OneToMany(mappedBy = "programSubject", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProgramSubjectPrerequisite> prerequisites;

    // Materias que dependen de esta ProgramSubject (referencias inversas)
    @OneToMany(mappedBy = "prerequisiteProgramSubject", fetch = FetchType.LAZY)
    private List<ProgramSubjectPrerequisite> requiredBy;

}
