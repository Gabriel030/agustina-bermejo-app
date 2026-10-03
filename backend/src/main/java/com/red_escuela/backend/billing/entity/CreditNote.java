package com.red_escuela.backend.billing.entity;
import com.red_escuela.backend.identity.entity.Student;

import jakarta.persistence.*;
import lombok.Getter;
import com.red_escuela.backend.shared.domain.Money;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.red_escuela.backend.identity.entity.Person;

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

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "amount", nullable = false)),
            @AttributeOverride(name = "currency", column = @Column(name = "amount_currency", nullable = false))
    })
    private Money amount;

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

    public Money getRemainingAmount() {
        Money applied = applications.stream()
                .map(CreditApplication::getAmountApplied)
                .reduce(Money.zero(amount.currency()), Money::plus);
        return amount.minus(applied);
    }

    public CreditApplication consume(Money amountApplied, Invoice invoice, Payment payment) {
        CreditApplication application = new CreditApplication();
        application.setCreditNote(this);
        application.setInvoice(invoice);
        application.setPayment(payment);
        application.setAmountApplied(amountApplied);
        applications.add(application);
        status = getRemainingAmount().isZero()
                ? CreditNoteStatus.CONSUMED
                : CreditNoteStatus.PARTIALLY_CONSUMED;
        return application;
    }
}
