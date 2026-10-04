package com.red_escuela.backend.billing.entity;

import com.red_escuela.backend.shared.time.ApplicationTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "scholarship_installment")
public class ScholarshipInstallment {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scholarship_id", nullable = false)
    private Scholarship scholarship;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InstallmentStatus status = InstallmentStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "cancelled_reason", length = 500)
    private String cancelledReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    void setScholarship(Scholarship scholarship) {
        this.scholarship = scholarship;
    }

    void markApplied(Invoice invoice) {
        this.status = InstallmentStatus.APPLIED;
        this.invoice = invoice;
        this.appliedAt = ApplicationTime.now();
    }

    void cancel(String reason) {
        this.status = InstallmentStatus.CANCELLED;
        this.cancelledReason = reason;
        this.cancelledAt = ApplicationTime.now();
    }
}
