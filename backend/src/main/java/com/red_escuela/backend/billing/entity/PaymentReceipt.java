package com.red_escuela.backend.billing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.red_escuela.backend.identity.entity.Person;

@Getter
@Setter
@Entity
@Table(name = "payment_receipt")
public class PaymentReceipt {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Sin esto, aprobar() y reject() concurrentes sobre el mismo comprobante (dos
    // Administrativos, o un doble clic) podían dejar el receipt en REJECTED mientras ya existía
    // un Payment CONFIRMED real y la Invoice en PAID detrás — ninguno de los dos caminos
    // detectaba al otro (hallazgo de auditoría 2026-09-04).
    @Version
    @Column(name = "version")
    private Long version;

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

    // El propio Alumno, vía CurrentStudentService — nunca un id de la URL. Nullable solo por
    // retrocompatibilidad con las filas del flujo ad-hoc de Épica 7 Fase 3, donde la Invoice
    // todavía no tiene enrollment vinculado y el studentId se recibe explícito.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id")
    private Person uploadedBy;

    @PrePersist
    void onCreate() {
        if (this.uploadedAt == null) {
            this.uploadedAt = LocalDateTime.now();
        }
    }
}
