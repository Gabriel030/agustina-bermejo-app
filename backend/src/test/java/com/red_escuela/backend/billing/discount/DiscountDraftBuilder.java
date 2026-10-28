package com.red_escuela.backend.billing.discount;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class DiscountDraftBuilder {
    private String name = "Promoción de prueba";
    private DiscountDefinitionType discountType = DiscountDefinitionType.COHORT_PROMOTION;
    private InvoiceType applicableInvoiceType = InvoiceType.MONTHLY_INSTALLMENT;
    private MonetaryAdjustment calculation = new MonetaryAdjustment.Proportional(Percentage.ofPoints(new BigDecimal("10")));
    private Integer installmentsTotal = 3;
    private LocalDate availableFrom = LocalDate.of(2031, 3, 1);
    private LocalDate availableUntil;
    private int priority = 1;
    private Money capPerInvoice;
    private Long programId = 1L;
    private Long programCohortId;
    private Long courseId;
    private LocalDate enrollmentWindowFrom;
    private LocalDate enrollmentWindowTo;
    private Integer quota;

    private DiscountDraftBuilder() {
    }

    public static DiscountDraftBuilder cohortPromotion() {
        return new DiscountDraftBuilder().withEnrollmentWindow(LocalDate.of(2000, 1, 1), LocalDate.of(2099, 12, 31));
    }

    public static DiscountDraftBuilder scholarship() {
        return new DiscountDraftBuilder().ofType(DiscountDefinitionType.SCHOLARSHIP).withoutScope().withoutCalculation().withoutInstallments();
    }

    public static DiscountDraftBuilder annualUpfront() {
        return new DiscountDraftBuilder().ofType(DiscountDefinitionType.ANNUAL_UPFRONT).forInvoiceType(InvoiceType.ANNUAL_UPFRONT).withoutScope().withoutInstallments();
    }

    public DiscountDraftBuilder named(String value) {
        this.name = value;
        return this;
    }

    public DiscountDraftBuilder ofType(DiscountDefinitionType value) {
        this.discountType = value;
        return this;
    }

    public DiscountDraftBuilder forInvoiceType(InvoiceType value) {
        this.applicableInvoiceType = value;
        return this;
    }

    public DiscountDraftBuilder withPercent(String points) {
        this.calculation = new MonetaryAdjustment.Proportional(Percentage.ofPoints(new BigDecimal(points)));
        return this;
    }

    public DiscountDraftBuilder withFixedAmount(String amount, Currency currency) {
        this.calculation = new MonetaryAdjustment.Nominal(Money.of(new BigDecimal(amount), currency));
        return this;
    }

    public DiscountDraftBuilder withoutCalculation() {
        this.calculation = null;
        return this;
    }

    public DiscountDraftBuilder withInstallments(Integer value) {
        this.installmentsTotal = value;
        return this;
    }

    public DiscountDraftBuilder withoutInstallments() {
        this.installmentsTotal = null;
        return this;
    }

    public DiscountDraftBuilder availableFrom(LocalDate value) {
        this.availableFrom = value;
        return this;
    }

    public DiscountDraftBuilder availableUntil(LocalDate value) {
        this.availableUntil = value;
        return this;
    }

    public DiscountDraftBuilder withPriority(int value) {
        this.priority = value;
        return this;
    }

    public DiscountDraftBuilder withCap(String amount, Currency currency) {
        this.capPerInvoice = Money.of(new BigDecimal(amount), currency);
        return this;
    }

    public DiscountDraftBuilder forProgram(Long value) {
        this.programId = value;
        this.courseId = null;
        return this;
    }

    public DiscountDraftBuilder forCourse(Long value) {
        this.courseId = value;
        this.programId = null;
        return this;
    }

    public DiscountDraftBuilder forCohort(Long value) {
        this.programCohortId = value;
        return this;
    }

    public DiscountDraftBuilder withoutScope() {
        this.programId = null;
        this.courseId = null;
        this.programCohortId = null;
        return this;
    }

    public DiscountDraftBuilder withEnrollmentWindow(LocalDate from, LocalDate to) {
        this.enrollmentWindowFrom = from;
        this.enrollmentWindowTo = to;
        return this;
    }

    public DiscountDraftBuilder withQuota(Integer value) {
        this.quota = value;
        return this;
    }

    public DiscountDefinitionDraft build() {
        return new DiscountDefinitionDraft(name, discountType, applicableInvoiceType, calculation, installmentsTotal, availableFrom, availableUntil, priority,
                capPerInvoice, programId, programCohortId, courseId, enrollmentWindowFrom, enrollmentWindowTo, quota);
    }
}
