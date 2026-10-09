package com.red_escuela.backend.billing.note.domain;

import com.red_escuela.backend.billing.invoice.domain.CreditNoteApplicationKind;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceCreditNoteApplication;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineCredit;
import com.red_escuela.backend.billing.invoice.domain.StudentSnapshot;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.time.ApplicationTime;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Immutable
@Table(name = "credit_note")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditNote {

    private static final int MAX_JUSTIFICATION_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private CreditNoteNumber creditNoteNumber;

    @Column(name = "student_current_account_id", nullable = false, updatable = false)
    private Long studentCurrentAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private Currency currency;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private Long invoiceId;

    @Column(name = "invoice_number", nullable = false, updatable = false, length = 20)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, updatable = false, length = 30)
    private CreditNoteReason reason;

    @Column(name = "justification", nullable = false, updatable = false, length = MAX_JUSTIFICATION_LENGTH)
    private String justification;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "offset_amount", nullable = false, updatable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "offset_amount_currency", nullable = false, updatable = false))})
    private NonNegativeMoney offsetAmount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "free_credit_amount", nullable = false, updatable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "free_credit_amount_currency", nullable = false, updatable = false))})
    private NonNegativeMoney freeCreditAmount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "total_amount", nullable = false, updatable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "total_amount_currency", nullable = false, updatable = false))})
    private NonNegativeMoney totalAmount;

    @Column(name = "issue_date", nullable = false, updatable = false)
    private LocalDate issueDate;

    @Column(name = "fact_date", nullable = false, updatable = false)
    private LocalDate factDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recipient_snapshot", nullable = false, updatable = false, columnDefinition = "jsonb")
    private StudentSnapshot recipientSnapshot;

    @Column(name = "authorization_request_id", updatable = false)
    private Long authorizationRequestId;

    @Column(name = "issued_by_person_id", updatable = false)
    private Long issuedByPersonId;

    @Column(name = "issued_by_process", updatable = false, length = 80)
    private String issuedByProcess;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "creditNote", cascade = CascadeType.PERSIST)
    @OrderBy("invoiceLineNumber asc")
    private List<CreditNoteLine> lines = new ArrayList<>();

    public static CreditNote issueCreditNoteForInvoice(Invoice invoice, CreditNoteReason reason, String justification, List<CreditNoteLineRequest> lineRequests,
                                                       CreditNoteNumber number, LocalDate issueDate, LocalDate factDate, Long authorizationRequestId,
                                                       StatusChangeActor actor) {
        Objects.requireNonNull(invoice, "invoice");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(issueDate, "issueDate");
        Objects.requireNonNull(factDate, "factDate");
        Objects.requireNonNull(actor, "actor");
        if (invoice.getDocumentStatus() != InvoiceDocumentStatus.ISSUED) {
            throw new IllegalArgumentException("A credit note adjusts an issued invoice");
        }
        if (justification == null || justification.isBlank() || justification.length() > MAX_JUSTIFICATION_LENGTH) {
            throw new IllegalArgumentException("The justification must have between 1 and " + MAX_JUSTIFICATION_LENGTH + " characters");
        }
        if (lineRequests == null || lineRequests.isEmpty()) {
            throw new IllegalArgumentException("A credit note needs at least one line");
        }
        CreditNote note = new CreditNote();
        CreditNoteDestination destination = CreditNoteDestination.ofInvoiceStatus(invoice.getPaymentStatus());
        Currency currency = invoice.getCurrency();
        note.creditNoteNumber = number;
        note.studentCurrentAccountId = invoice.getStudentCurrentAccountId();
        note.currency = currency;
        note.invoiceId = Objects.requireNonNull(invoice.getId(), "invoice.id");
        note.invoiceNumber = invoice.getInvoiceNumber().value();
        note.reason = reason;
        note.justification = justification.trim();
        note.issueDate = issueDate;
        note.factDate = factDate;
        note.recipientSnapshot = invoice.getStudentSnapshot();
        note.authorizationRequestId = authorizationRequestId;
        note.issuedByPersonId = actor.personId();
        note.issuedByProcess = actor.processName();
        note.recordedAt = ApplicationTime.now();
        NonNegativeMoney total = NonNegativeMoney.zero(currency);
        for (CreditNoteLineRequest request : lineRequests) {
            note.lines.add(new CreditNoteLine(note, request.lineNumber(), request.amount(), destination));
            total = total.plus(request.amount());
        }
        boolean offsetsDebt = destination == CreditNoteDestination.DEBT_OFFSET;
        note.offsetAmount = offsetsDebt ? total : NonNegativeMoney.zero(currency);
        note.freeCreditAmount = offsetsDebt ? NonNegativeMoney.zero(currency) : total;
        note.totalAmount = total;
        return note;
    }

    public InvoiceCreditNoteApplication toInvoiceCreditNoteApplication(NonNegativeMoney invoiceOutstandingAmount) {
        boolean cancelsInvoice = reason == CreditNoteReason.CANCELLATION && offsetAmount.equals(invoiceOutstandingAmount);
        CreditNoteApplicationKind kind = cancelsInvoice ? CreditNoteApplicationKind.CANCELLATION : CreditNoteApplicationKind.DEBT_REDUCTION;
        return new InvoiceCreditNoteApplication(kind, offsetAmount, freeCreditAmount,
                lines.stream().map(line -> new InvoiceLineCredit(line.getInvoiceLineNumber(), line.getAmount())).toList());
    }

    public List<CreditNoteLine> getLines() {
        return List.copyOf(lines);
    }

    public boolean offsetsDebt() {
        return !offsetAmount.value().isZero();
    }
}
