package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.DiscountSavingCalculator;
import com.red_escuela.backend.billing.discount.domain.EnrollmentKind;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.LectiveYearSpan;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountStatus;
import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.DiscountCandidateOrigin;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.operation.InvoiceDiscountCandidateStrategy;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentFacts;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentLookup;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Component
public class StudentDiscountCandidateStrategy implements InvoiceDiscountCandidateStrategy {

    private static final int MAX_NAME_LENGTH = 200;

    private final EnrollmentStudentDiscountReader discountReader;
    private final EnrollmentLectiveYearReader lectiveYearReader;
    private final BillingEnrollmentLookup enrollmentLookup;
    private final StudentDiscountConsumptionReader consumptionReader;

    public StudentDiscountCandidateStrategy(EnrollmentStudentDiscountReader discountReader, EnrollmentLectiveYearReader lectiveYearReader,
                                            BillingEnrollmentLookup enrollmentLookup, StudentDiscountConsumptionReader consumptionReader) {
        this.discountReader = discountReader;
        this.lectiveYearReader = lectiveYearReader;
        this.enrollmentLookup = enrollmentLookup;
        this.consumptionReader = consumptionReader;
    }

    @Override
    public DiscountCandidateOrigin origin() {
        return DiscountCandidateOrigin.STUDENT_DISCOUNT;
    }

    @Override
    public List<InvoiceDiscountCandidate> findDiscountCandidatesForInvoice(InvoiceIssuingContext context) {
        return findCandidates(context, discountReader::readApplicableDiscountsAsDetachedSnapshot);
    }

    @Override
    public List<InvoiceDiscountCandidate> findDiscountCandidatesUnderEnrollmentLock(InvoiceIssuingContext context) {
        return findCandidates(context, discountReader::lockEnrollmentAndReadApplicableDiscounts);
    }

    private List<InvoiceDiscountCandidate> findCandidates(InvoiceIssuingContext context, Function<EnrollmentReference, List<StudentDiscount>> reader) {
        InvoiceIssuingRequest request = context.request();
        InvoiceType invoiceType = request.invoiceType();
        if (invoiceType == InvoiceType.LATE_FEE) {
            return List.of();
        }
        Optional<EnrollmentReference> enrollment = enrollmentOf(request);
        if (enrollment.isEmpty()) {
            return List.of();
        }
        List<StudentDiscount> discounts = reader.apply(enrollment.get()).stream()
                .filter(discount -> discount.getApplicableInvoiceType() == invoiceType && discount.getCurrency() == context.facts().offeringCurrency())
                .toList();
        if (discounts.isEmpty()) {
            return List.of();
        }
        Optional<LectiveYearSpan> lectiveYear = invoiceType == InvoiceType.ENROLLMENT_FEE ? findLectiveYearOfEnrollmentFee(enrollment.get(), request) : Optional.empty();
        return discounts.stream()
                .filter(discount -> invoiceType == InvoiceType.ENROLLMENT_FEE ? isFirstEnrollmentFeeOfLectiveYear(discount, lectiveYear) : discount.coversPeriod(request.period()))
                .map(discount -> candidateOf(discount, context))
                .filter(candidate -> !candidate.discountAmount().value().isZero())
                .toList();
    }

    private Optional<EnrollmentReference> enrollmentOf(InvoiceIssuingRequest request) {
        if (request.programEnrollmentId() != null) {
            return Optional.of(EnrollmentReference.ofProgram(request.programEnrollmentId()));
        }
        return request.courseEnrollmentId() == null ? Optional.empty() : Optional.of(EnrollmentReference.ofCourse(request.courseEnrollmentId()));
    }

    private Optional<LectiveYearSpan> findLectiveYearOfEnrollmentFee(EnrollmentReference enrollment, InvoiceIssuingRequest request) {
        Optional<BillingEnrollmentFacts> facts = enrollment.kind() == EnrollmentKind.PROGRAM
                ? enrollmentLookup.findProgramEnrollmentFacts(enrollment.id())
                : enrollmentLookup.findCourseEnrollmentFacts(enrollment.id());
        return facts.flatMap(enrollmentFacts -> lectiveYearReader.findLectiveYearCovering(enrollment, enrollmentFacts, request.period()));
    }

    private boolean isFirstEnrollmentFeeOfLectiveYear(StudentDiscount discount, Optional<LectiveYearSpan> lectiveYear) {
        return discount.getStatus() == StudentDiscountStatus.ACTIVE
                && lectiveYear.map(span -> span.contains(discount.getFromPeriod())).orElse(false)
                && !consumptionReader.isAlreadyConsumed(discount);
    }

    private InvoiceDiscountCandidate candidateOf(StudentDiscount discount, InvoiceIssuingContext context) {
        InvoiceIssuingRequest request = context.request();
        NonNegativeMoney base = context.facts().chargeAmount();
        NonNegativeMoney saving = DiscountSavingCalculator.calculateSavingOnFinalPrice(discount.getCalculation(), base, Optional.ofNullable(discount.getCapPerInvoice()));
        boolean monthly = discount.getApplicableInvoiceType() == InvoiceType.MONTHLY_INSTALLMENT;
        Integer installmentNumber = monthly ? discount.periodRange().ordinalOf(request.period()) : null;
        Integer installmentsTotal = monthly ? discount.periodRange().monthCount() : null;
        return new InvoiceDiscountCandidate(discount.getDiscountType(), discount.getId(), discount.getId(), nameOf(discount), discount.getApplicableInvoiceType(), base, saving,
                installmentNumber, installmentsTotal, null, 1, discount.getPriority(), discount.getGrantedAt(), discount.getCalculation(),
                DiscountCandidateOrigin.STUDENT_DISCOUNT);
    }

    private String nameOf(StudentDiscount discount) {
        String prefix = discount.getDiscountType() == DiscountType.SCHOLARSHIP ? "Beca: " : "Descuento individual: ";
        String name = prefix + discount.getReason();
        return name.substring(0, Math.min(name.length(), MAX_NAME_LENGTH));
    }
}
