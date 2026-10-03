package com.red_escuela.backend.academicstructure.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.List;
import com.red_escuela.backend.entity.StatusType;

@Getter
@Setter
@Entity
@Table(name = "course_subject")
public class CourseSubject {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Course course;

    @Column(name = "name")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name= "subject_recurrence")
    private SubjectRecurrence subjectRecurrence;

    @Column(name = "curricular_year")
    private Integer curricularYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "curricular_term")
    private CurricularTerm curricularTerm;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "weekly_workload")
    private Integer weeklyWorkload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusType status;

    @Column(name = "qualification_type")
    private String qualificationType;

    @OneToMany(mappedBy = "courseSubject", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourseSubjectPrerequisite> prerequisites;

    @OneToMany(mappedBy = "prerequisiteCourseSubject", fetch = FetchType.LAZY)
    private List<CourseSubjectPrerequisite> requiredBy;

    @Column(name = "max_final_exam_attempts", nullable = false)
    private Integer maxFinalExamAttempts = Subject.DEFAULT_MAX_FINAL_EXAM_ATTEMPTS;

    public void setMaxFinalExamAttempts(Integer maxFinalExamAttempts) {
        this.maxFinalExamAttempts = maxFinalExamAttempts == null ? Subject.DEFAULT_MAX_FINAL_EXAM_ATTEMPTS : maxFinalExamAttempts;
    }
}
