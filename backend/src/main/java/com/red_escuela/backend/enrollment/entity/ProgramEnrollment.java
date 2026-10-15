package com.red_escuela.backend.enrollment.entity;

import org.springframework.modulith.NamedInterface;
import com.red_escuela.backend.identity.entity.Student;
import jakarta.persistence.*;
import com.red_escuela.backend.enrollment.events.ProgramEnrollmentGraduatedEvent;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.domain.AfterDomainEventPublication;
import org.springframework.data.domain.DomainEvents;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentRequiredException;

@NamedInterface("model")
@Getter
@Setter
@Entity
@Table(name = "program_enrollment")
public class ProgramEnrollment implements BillableEnrollment {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_cohort_id")
    private ProgramCohort programCohort;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_billing_template_id")
    private ProgramYearBillingTemplate currentBillingTemplate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admission_application_id")
    private ProgramEnrollmentApplication admissionApplication;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ProgramEnrollmentStatus status;

    @Column(name = "enrollment_date")
    private LocalDateTime enrollmentDate;

    @Column(name = "completion_date")
    private LocalDateTime completionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private EnrollmentPaymentStatus paymentStatus = EnrollmentPaymentStatus.UP_TO_DATE;

    @Column(name = "payment_status_updated_at")
    private LocalDateTime paymentStatusUpdatedAt;

    @OneToMany(mappedBy = "programEnrollment", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = false)
    private List<SubjectSectionEnrollment> sectionEnrollments;

    public ProgramYearBillingTemplate effectiveBillingTemplate() {
        return currentBillingTemplate != null ? currentBillingTemplate : programCohort.getTemplate();
    }

    public boolean isActive() {
        return status == ProgramEnrollmentStatus.ENROLLED || status == ProgramEnrollmentStatus.PAUSED;
    }

    public void requireActive() {
        if (!isActive()) {
            throw new ProgramEnrollmentRequiredException(id);
        }
    }

    public void complete(LocalDateTime completedAt) {
        requireActive();
        status = ProgramEnrollmentStatus.COMPLETED;
        completionDate = completedAt;
        pendingGraduationEvent = ProgramEnrollmentGraduatedEvent.graduatedOn(student.getId(), id, completedAt.toLocalDate());
    }

    @Column(name = "withdrawal_effective_date")
    private LocalDate withdrawalEffectiveDate;

    @Column(name = "withdrawal_registered_on")
    private LocalDate withdrawalRegisteredOn;

    @Column(name = "withdrawn_by_person_id")
    private Long withdrawnByPersonId;

    @Transient
    private ProgramEnrollmentGraduatedEvent pendingGraduationEvent;

    public void markWithdrawn(LocalDate effectiveDate, LocalDateTime completionMoment, LocalDate registeredOn, Long registeredByPersonId) {
        this.status = ProgramEnrollmentStatus.WITHDRAWN;
        this.withdrawalEffectiveDate = effectiveDate;
        this.withdrawalRegisteredOn = registeredOn;
        this.completionDate = completionMoment;
        this.withdrawnByPersonId = registeredByPersonId;
    }

    @DomainEvents
    List<Object> publishGraduationEvents() {
        return pendingGraduationEvent == null ? List.of() : List.of(pendingGraduationEvent);
    }

    @AfterDomainEventPublication
    void clearGraduationEvents() {
        pendingGraduationEvent = null;
    }
}
