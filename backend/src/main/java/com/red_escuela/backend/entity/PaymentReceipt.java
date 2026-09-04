package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// Minimal placeholder so the Épica 7 Fase 3 enrollment-with-payment wizard keeps compiling and
// working against the new Invoice — Fase 6.2 (Pagos y allocations) replaces this with the real
// design (rejection reason, unlimited retries, PaymentService.registrarPago on approval).
@Getter
@Setter
@Entity
@Table(name = "payment_receipt")
public class PaymentReceipt {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentReceiptStatus status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private Person reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @PrePersist
    void onCreate() {
        if (this.uploadedAt == null) {
            this.uploadedAt = LocalDateTime.now();
        }
    }
}
