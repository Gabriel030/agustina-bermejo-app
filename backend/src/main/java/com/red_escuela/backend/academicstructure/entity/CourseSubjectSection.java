package com.red_escuela.backend.academicstructure.entity;

import jakarta.persistence.*;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.persistence.PercentageConverter;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.entity.StatusType;

@Getter
@Setter
@Entity
@Table(name = "course_section")
public class CourseSubjectSection {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_subject_id", nullable = false)
    private CourseSubject courseSubject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    private Term term;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluation_scheme_id")
    private EvaluationScheme evaluationScheme;

    @Column(name = "schedule")
    private String schedule;

    @Column(name = "capacity")
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusType status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Convert(converter = PercentageConverter.class)
    @Column(name = "max_absence_percentage", precision = 7, scale = 6)
    private Percentage maxAbsenceRate;

    @OneToMany(mappedBy = "section", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourseSubjectSectionInstructor> instructors;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_offering_cycle_id")
    private AcademicOfferingCycle academicOfferingCycle;

    @Column(name = "pending_approval", nullable = false)
    private boolean pendingApproval = false;

    public boolean isOfferingApproved() {
        return academicOfferingCycle == null || academicOfferingCycle.wasApproved();
    }
}
