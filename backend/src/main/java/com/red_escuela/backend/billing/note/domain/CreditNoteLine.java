package com.red_escuela.backend.billing.note.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "credit_note_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditNoteLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_note_id", nullable = false, updatable = false)
    private CreditNote creditNote;

    @Column(name = "invoice_line_number", nullable = false, updatable = false)
    private int invoiceLineNumber;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "amount", nullable = false, updatable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "amount_currency", nullable = false, updatable = false))})
    private NonNegativeMoney amount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "offset_part", nullable = false, updatable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "offset_part_currency", nullable = false, updatable = false))})
    private NonNegativeMoney offsetPart;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "free_part", nullable = false, updatable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "free_part_currency", nullable = false, updatable = false))})
    private NonNegativeMoney freePart;

    CreditNoteLine(CreditNote creditNote, int invoiceLineNumber, NonNegativeMoney amount, CreditNoteDestination destination) {
        this.creditNote = creditNote;
        this.invoiceLineNumber = invoiceLineNumber;
        this.amount = amount;
        boolean offsetsDebt = destination == CreditNoteDestination.DEBT_OFFSET;
        this.offsetPart = offsetsDebt ? amount : NonNegativeMoney.zero(amount.value().currency());
        this.freePart = offsetsDebt ? NonNegativeMoney.zero(amount.value().currency()) : amount;
    }
}
