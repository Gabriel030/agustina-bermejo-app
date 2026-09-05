package com.red_escuela.backend.entity;

import com.red_escuela.backend.exception.InvalidProgramEnrollmentApplicationStateException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Solicitud de alta en una carrera, previa a un {@link ProgramEnrollment} real —
 * mismo patrón que {@link Student#getOnboardingStatus()} (Prospect) es un paso
 * previo a un Student confirmado, un nivel más arriba. La transición de estado
 * vive en la propia entidad (no desparramada en el service que la usa).
 */
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

    // Nullable: filas viejas (de antes de esta fase) pueden no tener comprobante.
    // El código nuevo siempre lo setea al create la solicitud (ver
    // ProgramEnrollmentApplicationService.solicitarConPago).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_receipt_id")
    private PaymentReceipt paymentReceipt;

    public void approve(Person decidedBy) {
        exigirPendiente();
        this.status = ProgramEnrollmentApplicationStatus.APPROVED;
        this.decidedBy = decidedBy;
        this.decidedAt = LocalDateTime.now();
    }

    public void reject(Person decidedBy, String reason) {
        exigirPendiente();
        this.status = ProgramEnrollmentApplicationStatus.REJECTED;
        this.decidedBy = decidedBy;
        this.decidedAt = LocalDateTime.now();
        this.rejectionReason = reason;
    }

    private void exigirPendiente() {
        if (status != ProgramEnrollmentApplicationStatus.PENDING) {
            throw new InvalidProgramEnrollmentApplicationStateException(id, status);
        }
    }
}
