package com.red_escuela.backend.academicstructure.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.util.List;
import com.red_escuela.backend.entity.StatusType;

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(name = "credits", nullable = false)
    private Integer credits = 0;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private ProgramSubjectCategory category = ProgramSubjectCategory.MANDATORY;

    @OneToMany(mappedBy = "programSubject", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProgramSubjectPrerequisite> prerequisites;

    @OneToMany(mappedBy = "prerequisiteProgramSubject", fetch = FetchType.LAZY)
    private List<ProgramSubjectPrerequisite> requiredBy;
}
