package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.billing.invoice.domain.event.InvoiceCancelledEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoiceDiscardedEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoiceIssuedEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoiceMarkedAsOverdueEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaymentReversedEvent;
import com.red_escuela.backend.billing.invoice.exception.CreditNoteExceedsCreditableAmountException;
import com.red_escuela.backend.billing.invoice.exception.DiscountExceedsLineAmountException;
import com.red_escuela.backend.billing.invoice.exception.DueDateBeforeIssueDateException;
import com.red_escuela.backend.billing.invoice.exception.IllegalInvoiceDocumentStatusTransitionException;
import com.red_escuela.backend.billing.invoice.exception.IllegalInvoicePaymentStatusTransitionException;
import com.red_escuela.backend.billing.invoice.exception.InvoiceLineNotAllowedForInvoiceTypeException;
import com.red_escuela.backend.billing.invoice.exception.InvoiceLinesEnrollmentMismatchException;
import com.red_escuela.backend.billing.invoice.exception.InvoiceStatusInconsistentWithAmountsException;
import com.red_escuela.backend.billing.invoice.exception.IssuedInvoiceCannotBeModifiedException;
import com.red_escuela.backend.billing.invoice.exception.StudentCurrentAccountCurrencyMismatchException;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.time.ApplicationTime;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Optional;

@Entity
@Table(name = "invoice")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invoice {

    private static final int MAX_ANNUAL_UPFRONT_LINES = 10;
    private static final int MAX_DISCARD_REASON_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "student_current_account_id", nullable = false, updatable = false)
    private Long studentCurrentAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, updatable = false)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, updatable = false)
    private InvoiceDocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", nullable = false, updatable = false)
    private InvoiceType invoiceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "issuing_reason", nullable = false, updatable = false)
    private InvoiceIssuingReason issuingReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_status", nullable = false)
    private InvoiceDocumentStatus documentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    private InvoicePaymentStatus paymentStatus;

    @Embedded
    private InvoiceNumber invoiceNumber;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "payment_deadline")
    private LocalDateTime paymentDeadline;

    @Column(name = "origin_invoice_id", updatable = false)
    private Long originInvoiceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "student_snapshot", columnDefinition = "jsonb")
    private StudentSnapshot studentSnapshot;

    @Embedded
    private InvoiceAmounts amounts;

    @Column(name = "discarded_reason", length = MAX_DISCARD_REASON_LENGTH)
    private String discardedReason;

    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber")
    private List<InvoiceLine> lines = new ArrayList<>();

    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<InvoiceStatusChange> statusChanges = new ArrayList<>();

    @Getter(AccessLevel.NONE)
    @Transient
    private List<java.util.function.Function<Long, Object>> recordedDomainEvents = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Invoice(InvoiceDraftHeader header) {
        this.studentCurrentAccountId = header.studentCurrentAccountId();
        this.currency = header.currency();
        this.documentType = header.documentType();
        this.invoiceType = header.invoiceType();
        this.issuingReason = header.issuingReason();
        this.originInvoiceId = header.originInvoiceId();
        this.amounts = InvoiceAmounts.zero(header.currency());
    }

    static Invoice createDraftInvoice(InvoiceDraftHeader header, StatusChangeActor actor) {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(actor, "actor");
        boolean isDebitNote = header.documentType() == InvoiceDocumentType.DEBIT_NOTE;
        if (isDebitNote != (header.invoiceType() == InvoiceType.LATE_FEE) || isDebitNote != (header.originInvoiceId() != null)) {
            throw new InvoiceLineNotAllowedForInvoiceTypeException();
        }
        Invoice invoice = new Invoice(header);
        invoice.changeDocumentStatus(InvoiceDocumentEvent.DRAFT_CREATED, actor, null, null);
        return invoice;
    }

    public List<InvoiceLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public InvoiceLine getPrimaryLine() {
        return lines.get(0);
    }

    public Currency currency() {
        return currency;
    }

    public FeeType getFeeType() {
        return getPrimaryLine().getFeeType();
    }

    public YearMonth getPeriod() {
        return getPrimaryLine().getPeriod();
    }

    public Long getTemplateId() {
        return getPrimaryLine().getTemplateId();
    }

    public Integer getEnrollmentFeeSlot() {
        return getPrimaryLine().getEnrollmentFeeSlot();
    }

    public boolean isPayable() {
        return documentStatus == InvoiceDocumentStatus.ISSUED && (paymentStatus == InvoicePaymentStatus.PENDING || paymentStatus == InvoicePaymentStatus.OVERDUE);
    }

    public Optional<Long> findProgramEnrollmentId() {
        return lines.stream().map(InvoiceLine::getProgramEnrollmentId).filter(Objects::nonNull).findFirst();
    }

    public Optional<Long> findCourseEnrollmentId() {
        return lines.stream().map(InvoiceLine::getCourseEnrollmentId).filter(Objects::nonNull).findFirst();
    }

    public void assignInvoiceToProgramEnrollment(Long programEnrollmentId) {
        Objects.requireNonNull(programEnrollmentId, "programEnrollmentId");
        lines.forEach(line -> line.assignProgramEnrollment(programEnrollmentId));
    }

    public void assignInvoiceToCourseEnrollment(Long courseEnrollmentId) {
        Objects.requireNonNull(courseEnrollmentId, "courseEnrollmentId");
        lines.forEach(line -> line.assignCourseEnrollment(courseEnrollmentId));
    }

    public List<InvoiceStatusChange> getStatusChanges() {
        return Collections.unmodifiableList(statusChanges);
    }

    public InvoiceLine addChargeLineToDraft(InvoiceLineDraft lineDraft) {
        Objects.requireNonNull(lineDraft, "lineDraft");
        requireDraftContentCanChange();
        requireSameCurrency(lineDraft.amount().value().currency());
        requireLineAllowedForInvoiceType(lineDraft);
        InvoiceLine line = new InvoiceLine(this, lines.size() + 1, lineDraft);
        lines.add(line);
        recalculateDraftAmounts();
        return line;
    }

    public void addDiscountToDraftLine(int lineNumber, InvoiceDiscountDraft discountDraft) {
        Objects.requireNonNull(discountDraft, "discountDraft");
        requireDraftContentCanChange();
        requireSameCurrency(discountDraft.discountAmount().value().currency());
        requireSameCurrency(discountDraft.baseAmount().value().currency());
        InvoiceLine line = findLine(lineNumber);
        if (line.getDiscount() != null
                || discountDraft.baseAmount().value().isGreaterThan(line.getAmount().value())
                || discountDraft.discountAmount().value().isGreaterThan(discountDraft.baseAmount().value())) {
            throw new DiscountExceedsLineAmountException();
        }
        line.attachDiscount(discountDraft);
        recalculateDraftAmounts();
    }

    public void submitDraftForPayment(LocalDateTime paymentDeadline, StatusChangeActor actor) {
        Objects.requireNonNull(paymentDeadline, "paymentDeadline");
        Objects.requireNonNull(actor, "actor");
        if (invoiceType != InvoiceType.ANNUAL_UPFRONT) {
            throw new IllegalInvoiceDocumentStatusTransitionException(documentStatus, InvoiceDocumentEvent.DRAFT_SUBMITTED_FOR_PAYMENT);
        }
        InvoiceDocumentStatusStateMachine.resolveNextStatus(documentStatus, InvoiceDocumentEvent.DRAFT_SUBMITTED_FOR_PAYMENT);
        requireAtLeastOneLine(InvoiceDocumentEvent.DRAFT_SUBMITTED_FOR_PAYMENT);
        changeDocumentStatus(InvoiceDocumentEvent.DRAFT_SUBMITTED_FOR_PAYMENT, actor, null, null);
        this.paymentDeadline = paymentDeadline;
    }

    public void issueInvoice(InvoiceNumber invoiceNumber, LocalDate issueDate, LocalDate dueDate, StudentSnapshot studentSnapshot,
                             StatusChangeActor actor) {
        Objects.requireNonNull(invoiceNumber, "invoiceNumber");
        Objects.requireNonNull(issueDate, "issueDate");
        Objects.requireNonNull(dueDate, "dueDate");
        Objects.requireNonNull(studentSnapshot, "studentSnapshot");
        Objects.requireNonNull(actor, "actor");
        InvoiceDocumentStatusStateMachine.resolveNextStatus(documentStatus, InvoiceDocumentEvent.INVOICE_ISSUED);
        requireAtLeastOneLine(InvoiceDocumentEvent.INVOICE_ISSUED);
        requireNumberOfDocumentType(invoiceNumber);
        if (dueDate.isBefore(issueDate)) {
            throw new DueDateBeforeIssueDateException();
        }
        this.invoiceNumber = invoiceNumber;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.studentSnapshot = studentSnapshot;
        changeDocumentStatus(InvoiceDocumentEvent.INVOICE_ISSUED, actor, null, issueDate);
        boolean zeroTotal = amounts.total().value().isZero();
        changePaymentStatus(zeroTotal ? InvoicePaymentEvent.ISSUED_WITH_ZERO_TOTAL : InvoicePaymentEvent.ISSUED_WITH_AMOUNT_DUE,
                amounts, issueDate, actor, null);
        recordedDomainEvents.add(invoiceId -> new InvoiceIssuedEvent(invoiceId, studentCurrentAccountId, documentType, invoiceType, amounts.total(), issueDate, dueDate));
        if (zeroTotal) {
            recordedDomainEvents.add(invoiceId -> new InvoicePaidEvent(invoiceId, studentCurrentAccountId, InvoicePaymentEvent.ISSUED_WITH_ZERO_TOTAL, issueDate));
        }
    }

    public void discardDraftInvoice(String reason, StatusChangeActor actor) {
        Objects.requireNonNull(actor, "actor");
        InvoiceDocumentStatusStateMachine.resolveNextStatus(documentStatus, InvoiceDocumentEvent.DRAFT_DISCARDED);
        if (reason == null || reason.isBlank() || reason.length() > MAX_DISCARD_REASON_LENGTH) {
            throw new IllegalArgumentException("The discard reason must have between 1 and " + MAX_DISCARD_REASON_LENGTH + " characters");
        }
        changeDocumentStatus(InvoiceDocumentEvent.DRAFT_DISCARDED, actor, reason, null);
        this.discardedReason = reason;
        lines.forEach(InvoiceLine::releaseChargeKey);
        recordedDomainEvents.add(invoiceId -> new InvoiceDiscardedEvent(invoiceId, reason));
    }

    public void markPaymentUnderReview(LocalDate businessDate, StatusChangeActor actor, String reason) {
        requireBusinessContext(businessDate, actor);
        InvoicePaymentEvent event = InvoicePaymentEvent.BANK_TRANSFER_RECEIPT_SUBMITTED_ON_TIME;
        InvoicePaymentStatusStateMachine.resolveNextStatus(paymentStatus, event);
        changePaymentStatus(event, amounts, businessDate, actor, reason);
    }

    public void releasePaymentReview(LocalDate businessDate, int gracePeriodDays, StatusChangeActor actor, String reason) {
        requireBusinessContext(businessDate, actor);
        InvoicePaymentEvent event = resolveEventByDueDate(businessDate, gracePeriodDays,
                InvoicePaymentEvent.BANK_TRANSFER_RECEIPT_REJECTED_BEFORE_DUE_DATE_PASSED,
                InvoicePaymentEvent.BANK_TRANSFER_RECEIPT_REJECTED_AFTER_DUE_DATE_PASSED);
        InvoicePaymentStatusStateMachine.resolveNextStatus(paymentStatus, event);
        changePaymentStatus(event, amounts, businessDate, actor, reason);
        if (event == InvoicePaymentEvent.BANK_TRANSFER_RECEIPT_REJECTED_AFTER_DUE_DATE_PASSED) {
            recordedDomainEvents.add(invoiceId -> new InvoiceMarkedAsOverdueEvent(invoiceId, dueDate, businessDate));
        }
    }

    public void allocateFullInvoicePayment(NonNegativeMoney allocatedAmount, LocalDate businessDate, StatusChangeActor actor) {
        requireBusinessContext(businessDate, actor);
        Objects.requireNonNull(allocatedAmount, "allocatedAmount");
        InvoicePaymentEvent event = InvoicePaymentEvent.FULL_PAYMENT_ALLOCATED;
        InvoicePaymentStatusStateMachine.resolveNextStatus(paymentStatus, event);
        changePaymentStatus(event, amounts.withFullPaymentAllocated(allocatedAmount), businessDate, actor, null);
        recordedDomainEvents.add(invoiceId -> new InvoicePaidEvent(invoiceId, studentCurrentAccountId, event, businessDate));
    }

    public void reversePaymentAllocation(LocalDate businessDate, int gracePeriodDays, StatusChangeActor actor, String reason) {
        requireBusinessContext(businessDate, actor);
        InvoicePaymentEvent event = resolveEventByDueDate(businessDate, gracePeriodDays,
                InvoicePaymentEvent.PAYMENT_ALLOCATION_REVERSED_BEFORE_DUE_DATE_PASSED,
                InvoicePaymentEvent.PAYMENT_ALLOCATION_REVERSED_AFTER_DUE_DATE_PASSED);
        InvoicePaymentStatusStateMachine.resolveNextStatus(paymentStatus, event);
        if (!amounts.hasPaidAmount()) {
            throw new IllegalInvoicePaymentStatusTransitionException(paymentStatus, event);
        }
        changePaymentStatus(event, amounts.withPaymentAllocationReversed(), businessDate, actor, reason);
        InvoicePaymentStatus resultingStatus = paymentStatus;
        recordedDomainEvents.add(invoiceId -> new InvoicePaymentReversedEvent(invoiceId, resultingStatus));
        if (event == InvoicePaymentEvent.PAYMENT_ALLOCATION_REVERSED_AFTER_DUE_DATE_PASSED) {
            recordedDomainEvents.add(invoiceId -> new InvoiceMarkedAsOverdueEvent(invoiceId, dueDate, businessDate));
        }
    }

    public void applyCreditNote(InvoiceCreditNoteApplication application, LocalDate businessDate, StatusChangeActor actor) {
        requireBusinessContext(businessDate, actor);
        Objects.requireNonNull(application, "application");
        InvoicePaymentEvent event = resolveCreditNoteEvent(application);
        InvoicePaymentStatusStateMachine.resolveNextStatus(paymentStatus, event);
        NonNegativeMoney offset = application.offsetAmount();
        if (offset.value().isGreaterThan(amounts.outstandingAmount().value())) {
            throw new CreditNoteExceedsCreditableAmountException();
        }
        boolean coversOutstanding = offset.equals(amounts.outstandingAmount());
        boolean offsetsNothing = offset.value().isZero();
        if ((event == InvoicePaymentEvent.CANCELLATION_CREDIT_NOTE_APPLIED && !coversOutstanding)
                || (event == InvoicePaymentEvent.PARTIAL_DEBT_REDUCTION_CREDIT_NOTE_APPLIED && offsetsNothing)) {
            throw new IllegalInvoicePaymentStatusTransitionException(paymentStatus, event);
        }
        if (event != InvoicePaymentEvent.CREDIT_NOTE_APPLIED_TO_PAID_INVOICE && !application.freeCreditAmount().value().isZero()) {
            throw new IllegalInvoicePaymentStatusTransitionException(paymentStatus, event);
        }
        List<NonNegativeMoney[]> splits = splitLineCredits(application);
        for (int i = 0; i < splits.size(); i++) {
            findLine(application.lineCredits().get(i).lineNumber()).requireCreditable(splits.get(i)[0], splits.get(i)[1]);
        }
        InvoiceAmounts newAmounts = amounts.withCreditNoteOffset(offset);
        for (int i = 0; i < splits.size(); i++) {
            findLine(application.lineCredits().get(i).lineNumber()).recordCredit(splits.get(i)[0], splits.get(i)[1]);
        }
        changePaymentStatus(event, newAmounts, businessDate, actor, application.kind().name());
        recordCreditNoteEvents(event, businessDate);
    }

    public void markInvoiceAsOverdue(LocalDate businessDate, StatusChangeActor actor) {
        requireBusinessContext(businessDate, actor);
        InvoicePaymentEvent event = InvoicePaymentEvent.DUE_DATE_PASSED;
        InvoicePaymentStatusStateMachine.resolveNextStatus(paymentStatus, event);
        changePaymentStatus(event, amounts, businessDate, actor, null);
        recordedDomainEvents.add(invoiceId -> new InvoiceMarkedAsOverdueEvent(invoiceId, dueDate, businessDate));
    }

    public List<Object> pullRecordedDomainEvents() {
        if (!recordedDomainEvents.isEmpty() && id == null) {
            throw new IllegalStateException("The invoice must be persisted before its domain events are pulled");
        }
        List<Object> pulled = recordedDomainEvents.stream().map(factory -> factory.apply(id)).toList();
        recordedDomainEvents.clear();
        return pulled;
    }

    void changeDocumentStatus(InvoiceDocumentEvent event, StatusChangeActor actor, String reason, LocalDate businessDate) {
        InvoiceDocumentStatus nextStatus = InvoiceDocumentStatusStateMachine.resolveNextStatus(documentStatus, event);
        if (event == InvoiceDocumentEvent.DRAFT_CONTENT_CHANGED) {
            return;
        }
        InvoiceDocumentStatus previousStatus = documentStatus;
        this.documentStatus = nextStatus;
        statusChanges.add(new InvoiceStatusChange(this, InvoiceStatusKind.DOCUMENT, previousStatus, nextStatus, event, reason, actor, businessDate));
    }

    void changePaymentStatus(InvoicePaymentEvent event, InvoiceAmounts newAmounts, LocalDate businessDate, StatusChangeActor actor,
                             String reason) {
        InvoicePaymentStatus nextStatus = InvoicePaymentStatusStateMachine.resolveNextStatus(paymentStatus, event);
        requireStatusConsistentWithAmounts(nextStatus, newAmounts);
        InvoicePaymentStatus previousStatus = paymentStatus;
        this.paymentStatus = nextStatus;
        this.amounts = newAmounts;
        statusChanges.add(new InvoiceStatusChange(this, InvoiceStatusKind.PAYMENT, previousStatus, nextStatus, event, reason, actor, businessDate));
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = ApplicationTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = ApplicationTime.now();
    }

    private void requireDraftContentCanChange() {
        if (documentStatus == InvoiceDocumentStatus.ISSUED) {
            throw new IssuedInvoiceCannotBeModifiedException();
        }
        changeDocumentStatus(InvoiceDocumentEvent.DRAFT_CONTENT_CHANGED, null, null, null);
    }

    private void requireSameCurrency(Currency otherCurrency) {
        if (otherCurrency != currency) {
            throw new StudentCurrentAccountCurrencyMismatchException();
        }
    }

    private void requireLineAllowedForInvoiceType(InvoiceLineDraft lineDraft) {
        int maxLines = invoiceType == InvoiceType.ANNUAL_UPFRONT ? MAX_ANNUAL_UPFRONT_LINES : 1;
        if (lineDraft.feeType() != expectedFeeType() || lines.size() >= maxLines) {
            throw new InvoiceLineNotAllowedForInvoiceTypeException();
        }
        if (!lines.isEmpty()) {
            InvoiceLine first = lines.get(0);
            if (!Objects.equals(first.getProgramEnrollmentId(), lineDraft.programEnrollmentId())
                    || !Objects.equals(first.getCourseEnrollmentId(), lineDraft.courseEnrollmentId())) {
                throw new InvoiceLinesEnrollmentMismatchException();
            }
        }
    }

    private FeeType expectedFeeType() {
        return switch (invoiceType) {
            case MONTHLY_INSTALLMENT, ANNUAL_UPFRONT -> FeeType.MONTHLY_INSTALLMENT;
            case ENROLLMENT_FEE -> FeeType.ENROLLMENT_FEE;
            case EXAM_FEE -> FeeType.EXAM_FEE;
            case RETAKEN_SUBJECT_FEE -> FeeType.RETAKEN_SUBJECT_FEE;
            case DEGREE_ISSUANCE_FEE -> FeeType.DEGREE_ISSUANCE_FEE;
            case ADMINISTRATIVE_FEE -> FeeType.ADMINISTRATIVE_FEE;
            case LATE_FEE -> FeeType.LATE_FEE;
        };
    }

    private void recalculateDraftAmounts() {
        this.amounts = InvoiceAmounts.calculateDraftAmounts(currency, lines);
    }

    private InvoiceLine findLine(int lineNumber) {
        return lines.stream()
                .filter(line -> line.getLineNumber() == lineNumber)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The invoice has no line " + lineNumber));
    }

    private void requireAtLeastOneLine(InvoiceDocumentEvent event) {
        if (lines.isEmpty()) {
            throw new IllegalInvoiceDocumentStatusTransitionException(documentStatus, event);
        }
    }

    private void requireNumberOfDocumentType(InvoiceNumber number) {
        if (!number.belongsTo(documentType)) {
            throw new IllegalArgumentException("The invoice number does not belong to the document type " + documentType);
        }
    }

    private void requireBusinessContext(LocalDate businessDate, StatusChangeActor actor) {
        Objects.requireNonNull(businessDate, "businessDate");
        Objects.requireNonNull(actor, "actor");
    }

    private InvoicePaymentEvent resolveEventByDueDate(LocalDate businessDate, int gracePeriodDays, InvoicePaymentEvent beforeEvent,
                                                      InvoicePaymentEvent afterEvent) {
        if (gracePeriodDays < 0) {
            throw new IllegalArgumentException("gracePeriodDays must not be negative");
        }
        if (dueDate == null || !businessDate.isAfter(dueDate.plusDays(gracePeriodDays))) {
            return beforeEvent;
        }
        return afterEvent;
    }

    private List<NonNegativeMoney[]> splitLineCredits(InvoiceCreditNoteApplication application) {
        List<NonNegativeMoney[]> splits = new ArrayList<>();
        NonNegativeMoney offsetLeft = application.offsetAmount();
        for (InvoiceLineCredit lineCredit : application.lineCredits()) {
            NonNegativeMoney offsetPart = lineCredit.amount().value().isGreaterThan(offsetLeft.value()) ? offsetLeft : lineCredit.amount();
            splits.add(new NonNegativeMoney[]{offsetPart, NonNegativeMoney.of(lineCredit.amount().minus(offsetPart))});
            offsetLeft = NonNegativeMoney.of(offsetLeft.minus(offsetPart));
        }
        return splits;
    }

    private InvoicePaymentEvent resolveCreditNoteEvent(InvoiceCreditNoteApplication application) {
        if (application.kind() == CreditNoteApplicationKind.CANCELLATION) {
            return InvoicePaymentEvent.CANCELLATION_CREDIT_NOTE_APPLIED;
        }
        if (paymentStatus == InvoicePaymentStatus.PAID) {
            return InvoicePaymentEvent.CREDIT_NOTE_APPLIED_TO_PAID_INVOICE;
        }
        return application.offsetAmount().equals(amounts.outstandingAmount())
                ? InvoicePaymentEvent.DEBT_REDUCTION_CREDIT_NOTE_SETTLED_INVOICE
                : InvoicePaymentEvent.PARTIAL_DEBT_REDUCTION_CREDIT_NOTE_APPLIED;
    }

    private void recordCreditNoteEvents(InvoicePaymentEvent event, LocalDate businessDate) {
        switch (event) {
            case CANCELLATION_CREDIT_NOTE_APPLIED -> recordedDomainEvents.add(invoiceId -> new InvoiceCancelledEvent(invoiceId, businessDate));
            case DEBT_REDUCTION_CREDIT_NOTE_SETTLED_INVOICE ->
                    recordedDomainEvents.add(invoiceId -> new InvoicePaidEvent(invoiceId, studentCurrentAccountId, event, businessDate));
            default -> {
            }
        }
    }

    private void requireStatusConsistentWithAmounts(InvoicePaymentStatus status, InvoiceAmounts candidate) {
        boolean consistent = switch (status) {
            case PENDING, PAYMENT_UNDER_REVIEW, OVERDUE -> !candidate.hasPaidAmount() && candidate.hasOutstandingAmount();
            case PAID -> !candidate.hasOutstandingAmount();
            case CANCELLED -> !candidate.hasPaidAmount() && !candidate.hasOutstandingAmount()
                    && candidate.creditedAmount().equals(candidate.total());
        };
        if (!consistent) {
            throw new InvoiceStatusInconsistentWithAmountsException(status + " with " + candidate);
        }
    }
}
