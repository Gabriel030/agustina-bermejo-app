package com.red_escuela.backend.enrollment.entity;

import org.springframework.modulith.NamedInterface;
import com.red_escuela.backend.identity.entity.Student;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
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

    public boolean isEnrollmentFeeMonth(YearMonth period) {
        return programCohort != null && programCohort.isEnrollmentFeeMonth(period);
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
    }
}
