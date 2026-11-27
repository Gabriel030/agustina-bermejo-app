package com.red_escuela.backend.evaluation.entity;
import com.red_escuela.backend.identity.entity.Person;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.evaluation.exception.InvalidSectionEvaluationConfigTransitionException;
import com.red_escuela.backend.evaluation.exception.SubmissionWindowClosedException;

@Getter
@Setter
@Entity
@Table(name = "section_evaluation_config")
public class SectionEvaluationConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_section_id", unique = true)
    private SubjectSection subjectSection;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_section_id", unique = true)
    private CourseSubjectSection courseSection;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SectionEvaluationConfigStatus status;

    @Column(name = "tp_count")
    private Integer tpCount;

    @Column(name = "tp1_due_date")
    private LocalDate tp1DueDate;

    @Column(name = "tp2_due_date")
    private LocalDate tp2DueDate;

    @Column(name = "tp3_due_date")
    private LocalDate tp3DueDate;

    @Column(name = "exam1_date")
    private LocalDate exam1Date;

    @Column(name = "exam2_date")
    private LocalDate exam2Date;

    @Column(name = "exam3_date")
    private LocalDate exam3Date;

    @Column(name = "exam4_date")
    private LocalDate exam4Date;

    @Column(name = "final_call1_date")
    private LocalDate finalCall1Date;

    @Column(name = "final_call2_date")
    private LocalDate finalCall2Date;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private Person reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Transient
    private int evaluationConfigDueDays;

    public SectionEvaluationConfig withEvaluationConfigDueDays(int dueDays) {
        this.evaluationConfigDueDays = dueDays;
        return this;
    }

    public boolean isEditable() {
        return status == SectionEvaluationConfigStatus.DRAFT || status == SectionEvaluationConfigStatus.REJECTED;
    }

    public boolean canBeSubmitted(LocalDateTime now) {
        return status == SectionEvaluationConfigStatus.REJECTED
                || (status == SectionEvaluationConfigStatus.DRAFT && isSubmissionWindowOpen(now));
    }

    public void submitForApproval(LocalDateTime now) {
        if (!isEditable()) {
            throw new InvalidSectionEvaluationConfigTransitionException(status, "enviar a aprobación");
        }
        if (!canBeSubmitted(now)) {
            throw new SubmissionWindowClosedException(ownerSectionId());
        }
        status = SectionEvaluationConfigStatus.PENDING_APPROVAL;
        submittedAt = now;
        rejectionReason = null;
    }

    private boolean isSubmissionWindowOpen(LocalDateTime now) {
        LocalDateTime termStart = term().getStartDate();
        return termStart == null || !now.toLocalDate().isAfter(termStart.toLocalDate().plusDays(evaluationConfigDueDays));
    }

    public boolean isOwnedByCourseSection() {
        return courseSection != null;
    }

    public Long ownerSectionId() {
        return courseSection != null ? courseSection.getId() : subjectSection.getId();
    }

    public Term term() {
        return courseSection != null ? courseSection.getTerm() : subjectSection.getTerm();
    }

    public EvaluationScheme sectionEvaluationScheme() {
        return courseSection != null ? courseSection.getEvaluationScheme() : subjectSection.getEvaluationScheme();
    }

    public String subjectName() {
        return courseSection != null ? courseSection.getModuleName() : subjectSection.getSubject().getName();
    }

    public Long subjectId() {
        return courseSection != null ? null : subjectSection.getSubject().getId();
    }

    public Long courseSubjectId() {
        return courseSection != null ? courseSection.getCourseSubject().getId() : null;
    }

    public String sectionName() {
        return courseSection != null ? courseSection.getName() : subjectSection.getName();
    }
}
