package com.red_escuela.backend.entity;

import com.red_escuela.backend.exception.InvalidPaymentReceiptStateException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Comprobante de pago subido por el propio Alumno — paso previo sin confirmar,
 * nunca un {@link Payment} real (mismo criterio de dominio que {@code Prospect}
 * es un paso previo a {@code Student}). Cuando se aprueba, se crea el
 * {@link Payment} real correspondiente ({@link com.red_escuela.backend.service.InvoiceService}) —
 * esta entidad no lo hace por sí sola, la decisión de approve es de un service
 * que orquesta ambas cosas en una transacción.
 */
@Getter
@Setter
@Entity
@Table(name = "payment_receipt")
public class PaymentReceipt {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentReceiptStatus status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private Person reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    public void approve(Person reviewedBy) {
        requirePending();
        this.status = PaymentReceiptStatus.APPROVED;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = LocalDateTime.now();
    }

    public void reject(Person reviewedBy, String reason) {
        requirePending();
        this.status = PaymentReceiptStatus.REJECTED;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = LocalDateTime.now();
        this.rejectionReason = reason;
    }

    private void requirePending() {
        if (status != PaymentReceiptStatus.PENDING_REVIEW) {
            throw new InvalidPaymentReceiptStateException(id, status);
        }
    }
}
