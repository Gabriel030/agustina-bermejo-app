package com.red_escuela.backend.billing.entity;
import com.red_escuela.backend.identity.entity.Student;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.red_escuela.backend.identity.entity.Person;

// Aggregate Root — mismo criterio que Invoice (Fase 6.1): el único método que cambia status y
// crea un CreditApplication es consume(), nunca un setStatus directo desde un service.
@Getter
@Setter
@Entity
@Table(name = "credit_note")
public class CreditNote {

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

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_type", nullable = false)
    private CreditNoteReasonType reasonType;

    @Column(name = "reason", length = 500, nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CreditNoteStatus status = CreditNoteStatus.AVAILABLE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private Person createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToMany
    @JoinTable(name = "credit_note_source_invoice",
            joinColumns = @JoinColumn(name = "credit_note_id"),
            inverseJoinColumns = @JoinColumn(name = "invoice_id"))
    private List<Invoice> sourceInvoices = new ArrayList<>();

    @OneToMany(mappedBy = "creditNote", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CreditApplication> applications = new ArrayList<>();

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public BigDecimal getRemainingAmount() {
        BigDecimal applied = applications.stream()
                .map(CreditApplication::getAmountApplied)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return amount.subtract(applied);
    }

    public CreditApplication consume(BigDecimal amountApplied, Invoice invoice, Payment payment) {
        CreditApplication application = new CreditApplication();
        application.setCreditNote(this);
        application.setInvoice(invoice);
        application.setPayment(payment);
        application.setAmountApplied(amountApplied);
        applications.add(application);
        status = getRemainingAmount().compareTo(BigDecimal.ZERO) == 0
                ? CreditNoteStatus.CONSUMED
                : CreditNoteStatus.PARTIALLY_CONSUMED;
        return application;
    }
}
