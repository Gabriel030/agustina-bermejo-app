package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.billing.invoice.exception.CreditNoteExceedsCreditableAmountException;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.time.ApplicationTime;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.red_escuela.backend.billing.invoice.exception.InvoiceLinesEnrollmentMismatchException;
import java.time.LocalDateTime;
import java.time.YearMonth;

@Entity
@Table(name = "invoice_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, updatable = false)
    private Currency currency;

    @Column(name = "line_number", nullable = false, updatable = false)
    private int lineNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", nullable = false, updatable = false)
    private FeeType feeType;

    @Column(name = "concept", nullable = false, updatable = false, length = 200)
    private String concept;

    @Column(name = "period", nullable = false, updatable = false)
    private YearMonth period;

    @Column(name = "program_enrollment_id")
    private Long programEnrollmentId;

    @Column(name = "course_enrollment_id")
    private Long courseEnrollmentId;

    @Column(name = "subject_id", updatable = false)
    private Long subjectId;

    @Column(name = "exam_board_registration_id", updatable = false)
    private Long examBoardRegistrationId;

    @Column(name = "price_revision_id", updatable = false)
    private Long priceRevisionId;

    @Column(name = "template_id", updatable = false)
    private Long templateId;

    @Column(name = "enrollment_fee_slot", updatable = false)
    private Integer enrollmentFeeSlot;

    @Column(name = "charge_key", nullable = false, updatable = false, length = 120)
    private String chargeKey;

    @Column(name = "charge_key_released_at")
    private LocalDateTime chargeKeyReleasedAt;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "amount", nullable = false, updatable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "amount_currency", nullable = false, updatable = false))})
    private NonNegativeMoney amount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "discount_amount", nullable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "discount_amount_currency", nullable = false))})
    private NonNegativeMoney discountAmount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "credited_amount", nullable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "credited_amount_currency", nullable = false))})
    private NonNegativeMoney creditedAmount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "free_credited_amount", nullable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "free_credited_amount_currency", nullable = false))})
    private NonNegativeMoney freeCreditedAmount;

    @OneToOne(mappedBy = "invoiceLine", cascade = CascadeType.ALL, orphanRemoval = true)
    private InvoiceDiscount discount;

    InvoiceLine(Invoice invoice, int lineNumber, InvoiceLineDraft draft) {
        Currency lineCurrency = draft.amount().value().currency();
        this.invoice = invoice;
        this.currency = lineCurrency;
        this.lineNumber = lineNumber;
        this.feeType = draft.feeType();
        this.concept = draft.concept();
        this.period = draft.period();
        this.programEnrollmentId = draft.programEnrollmentId();
        this.courseEnrollmentId = draft.courseEnrollmentId();
        this.subjectId = draft.subjectId();
        this.examBoardRegistrationId = draft.examBoardRegistrationId();
        this.priceRevisionId = draft.priceRevisionId();
        this.templateId = draft.templateId();
        this.enrollmentFeeSlot = draft.enrollmentFeeSlot();
        this.chargeKey = draft.chargeKey();
        this.amount = draft.amount();
        this.discountAmount = NonNegativeMoney.zero(lineCurrency);
        this.creditedAmount = NonNegativeMoney.zero(lineCurrency);
        this.freeCreditedAmount = NonNegativeMoney.zero(lineCurrency);
    }

    void attachDiscount(InvoiceDiscountDraft draft) {
        this.discount = new InvoiceDiscount(this, draft);
        this.discountAmount = draft.discountAmount();
    }

    void assignProgramEnrollment(Long enrollmentId) {
        if (programEnrollmentId != null || courseEnrollmentId != null) {
            throw new InvoiceLinesEnrollmentMismatchException();
        }
        this.programEnrollmentId = enrollmentId;
    }

    void assignCourseEnrollment(Long enrollmentId) {
        if (programEnrollmentId != null || courseEnrollmentId != null) {
            throw new InvoiceLinesEnrollmentMismatchException();
        }
        this.courseEnrollmentId = enrollmentId;
    }

    void releaseChargeKey() {
        this.chargeKeyReleasedAt = ApplicationTime.now();
    }

    void requireCreditable(NonNegativeMoney offsetPart, NonNegativeMoney freePart) {
        if (offsetPart.plus(freePart).value().isGreaterThan(calculateCreditableAmount().value())) {
            throw new CreditNoteExceedsCreditableAmountException();
        }
    }

    void recordCredit(NonNegativeMoney offsetPart, NonNegativeMoney freePart) {
        requireCreditable(offsetPart, freePart);
        this.creditedAmount = creditedAmount.plus(offsetPart);
        this.freeCreditedAmount = freeCreditedAmount.plus(freePart);
    }

    public NonNegativeMoney calculateNetAmount() {
        return NonNegativeMoney.of(amount.minus(discountAmount));
    }

    public NonNegativeMoney calculateCreditableAmount() {
        return NonNegativeMoney.of(calculateNetAmount().minus(creditedAmount.plus(freeCreditedAmount)));
    }
}
