package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.billing.service.PriceRevisionService;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.domain.InvoiceRecipient;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingContext;

@Component
public class InvoiceIssuingContextLoader {
    private static final Set<FeeType> UNIQUE_PER_PERIOD = Set.of(FeeType.MONTHLY_INSTALLMENT, FeeType.ENROLLMENT_FEE);
    private final PriceRevisionService priceRevisionService;
    private final InvoiceRepository invoiceRepository;
    private final ProgramYearBillingTemplateRepository templateRepository;

    public InvoiceIssuingContextLoader(PriceRevisionService priceRevisionService, InvoiceRepository invoiceRepository,
                                 ProgramYearBillingTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
        this.priceRevisionService = priceRevisionService;
        this.invoiceRepository = invoiceRepository;
    }

    public InvoiceIssuingContext load(InvoiceIssuingRequest request, LocalDate today) {
        Currency currency = switch (request.target()) {
            case InvoiceRecipient.ProgramEnrollmentRecipient t -> t.enrollment().getProgram().getCurrency();
            case InvoiceRecipient.CourseEnrollmentRecipient t -> t.enrollment().getCourse().getCurrency();
            case InvoiceRecipient.ProgramApplicantRecipient t -> t.program().getCurrency();
            case InvoiceRecipient.CourseApplicantRecipient t -> t.course().getCurrency();
        };
        Optional<PriceRevision> revision = request.reason() == InvoiceIssuingReason.AD_HOC_CHARGE ? Optional.empty() : findRevision(request, today);
        Money amount = request.reason() == InvoiceIssuingReason.AD_HOC_CHARGE ? request.explicitAmount() : revision.map(PriceRevision::getMonthlyAmount).orElse(null);
        Long existing = existingInvoice(request).map(Invoice::getId).orElse(null);
        Percentage entryDiscount = request.entry() ? entryDiscount(request.target()) : null;
        MonetaryAdjustment upfront = request.reason() == InvoiceIssuingReason.UPFRONT && request.selection() != null
                ? request.selection().getPaymentPlanOption().getUpfrontDiscount() : null;
        return new InvoiceIssuingContext(request, today, currency, amount, revision.orElse(null), existing, entryDiscount, upfront, templateEnrollmentFeeCount(request));
    }

    private Integer templateEnrollmentFeeCount(InvoiceIssuingRequest request) {
        if (request.feeType() != FeeType.ENROLLMENT_FEE || request.templateId() == null) {
            return null;
        }
        return templateRepository.findEnrollmentFeeCountForShare(request.templateId()).orElse(null);
    }

    private Optional<PriceRevision> findRevision(InvoiceIssuingRequest request, LocalDate today) {
        return switch (request.target()) {
            case InvoiceRecipient.ProgramEnrollmentRecipient t -> priceRevisionService.findRevisionForProgram(t.enrollment().getProgram().getId(), request.feeType(), today);
            case InvoiceRecipient.CourseEnrollmentRecipient t -> priceRevisionService.findRevisionForCourse(t.enrollment().getCourse().getId(), request.feeType(), today);
            case InvoiceRecipient.ProgramApplicantRecipient t -> priceRevisionService.findRevisionForProgram(t.program().getId(), request.feeType(), today);
            case InvoiceRecipient.CourseApplicantRecipient t -> priceRevisionService.findRevisionForCourse(t.course().getId(), request.feeType(), today);
        };
    }

    private Percentage entryDiscount(InvoiceRecipient target) {
        return switch (target) {
            case InvoiceRecipient.ProgramEnrollmentRecipient t -> t.enrollment().getProgramCohort() == null ? null : t.enrollment().getProgramCohort().getEntryFeeDiscount();
            case InvoiceRecipient.ProgramApplicantRecipient t -> t.cohort().getEntryFeeDiscount();
            default -> null;
        };
    }

    private Optional<Invoice> existingInvoice(InvoiceIssuingRequest request) {
        if (request.reason() == InvoiceIssuingReason.AD_HOC_CHARGE || !UNIQUE_PER_PERIOD.contains(request.feeType())) {
            return Optional.empty();
        }
        return switch (request.target()) {
            case InvoiceRecipient.ProgramEnrollmentRecipient t -> {
                Long id = t.enrollment().getId();
                Optional<Invoice> bySlot = request.feeType() == FeeType.ENROLLMENT_FEE && request.templateId() != null && request.slot() != null
                        ? invoiceRepository.findFirstByProgramEnrollmentIdAndFeeTypeAndTemplateIdAndEnrollmentFeeSlot(id, request.feeType(), request.templateId(), request.slot())
                        : Optional.empty();
                yield bySlot.or(() -> invoiceRepository.findFirstByProgramEnrollmentIdAndFeeTypeAndPeriod(id, request.feeType(), request.period()));
            }
            case InvoiceRecipient.CourseEnrollmentRecipient t -> invoiceRepository.findFirstByCourseEnrollmentIdAndFeeTypeAndPeriod(t.enrollment().getId(), request.feeType(), request.period());
            default -> Optional.empty();
        };
    }
}
