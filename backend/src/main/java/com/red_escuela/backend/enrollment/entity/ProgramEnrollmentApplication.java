package com.red_escuela.backend.enrollment.entity;
import com.red_escuela.backend.shared.time.ApplicationTime;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;

import com.red_escuela.backend.enrollment.exception.InvalidProgramEnrollmentApplicationStateException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.red_escuela.backend.billing.entity.PaymentReceipt;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.academicstructure.entity.Program;

@Getter
@Setter
@Entity
@Table(name = "program_enrollment_application")
public class ProgramEnrollmentApplication {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_cohort_id")
    private ProgramCohort programCohort;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProgramEnrollmentApplicationStatus status;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_id")
    private Person decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_receipt_id")
    private PaymentReceipt paymentReceipt;

    public void approve(Person decidedBy) {
        requirePending();
        this.status = ProgramEnrollmentApplicationStatus.APPROVED;
        this.decidedBy = decidedBy;
        this.decidedAt = ApplicationTime.now();
    }

    public void reject(Person decidedBy, String reason) {
        requirePending();
        this.status = ProgramEnrollmentApplicationStatus.REJECTED;
        this.decidedBy = decidedBy;
        this.decidedAt = ApplicationTime.now();
        this.rejectionReason = reason;
    }

    private void requirePending() {
        if (status != ProgramEnrollmentApplicationStatus.PENDING) {
            throw new InvalidProgramEnrollmentApplicationStateException(id, status);
        }
    }
}
