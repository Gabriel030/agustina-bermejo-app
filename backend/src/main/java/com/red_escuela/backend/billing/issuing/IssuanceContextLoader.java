package com.red_escuela.backend.billing.issuing;

import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.IssueReason;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.service.PriceRevisionService;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

@Component
public class IssuanceContextLoader {
    private static final Set<FeeType> UNIQUE_PER_PERIOD = Set.of(FeeType.RECURRING_FEE, FeeType.ENROLLMENT_FEE, FeeType.SUBSCRIPTION_FEE);
    private final PriceRevisionService priceRevisionService;
    private final InvoiceRepository invoiceRepository;

    public IssuanceContextLoader(PriceRevisionService priceRevisionService, InvoiceRepository invoiceRepository) {
        this.priceRevisionService = priceRevisionService;
        this.invoiceRepository = invoiceRepository;
    }

    public IssuanceContext load(ChargeRequest request, LocalDate today) {
        Currency currency = switch (request.target()) {
            case ChargeTarget.ProgramStudent t -> t.enrollment().getProgram().getCurrency();
            case ChargeTarget.CourseStudent t -> t.enrollment().getCourse().getCurrency();
            case ChargeTarget.ProgramApplicant t -> t.program().getCurrency();
            case ChargeTarget.CourseApplicant t -> t.course().getCurrency();
        };
        Optional<PriceRevision> revision = request.reason() == IssueReason.AD_HOC ? Optional.empty() : findRevision(request, today);
        Money amount = request.reason() == IssueReason.AD_HOC ? request.explicitAmount() : revision.map(PriceRevision::getMonthlyAmount).orElse(null);
        Long existing = existingInvoice(request).map(Invoice::getId).orElse(null);
        Percentage entryDiscount = request.entry() ? entryDiscount(request.target()) : null;
        MonetaryAdjustment upfront = request.reason() == IssueReason.UPFRONT && request.selection() != null
                ? request.selection().getPaymentPlanOption().getUpfrontDiscount() : null;
        return new IssuanceContext(request, today, currency, amount, revision.orElse(null), existing, entryDiscount, upfront);
    }

    private Optional<PriceRevision> findRevision(ChargeRequest request, LocalDate today) {
        return switch (request.target()) {
            case ChargeTarget.ProgramStudent t -> priceRevisionService.findRevisionForProgram(t.enrollment().getProgram().getId(), request.feeType(), today);
            case ChargeTarget.CourseStudent t -> priceRevisionService.findRevisionForCourse(t.enrollment().getCourse().getId(), request.feeType(), today);
            case ChargeTarget.ProgramApplicant t -> priceRevisionService.findRevisionForProgram(t.program().getId(), request.feeType(), today);
            case ChargeTarget.CourseApplicant t -> priceRevisionService.findRevisionForCourse(t.course().getId(), request.feeType(), today);
        };
    }

    private Percentage entryDiscount(ChargeTarget target) {
        return switch (target) {
            case ChargeTarget.ProgramStudent t -> t.enrollment().getProgramCohort() == null ? null : t.enrollment().getProgramCohort().getEntryFeeDiscount();
            case ChargeTarget.ProgramApplicant t -> t.cohort().getEntryFeeDiscount();
            default -> null;
        };
    }

    private Optional<Invoice> existingInvoice(ChargeRequest request) {
        if (request.reason() == IssueReason.AD_HOC || !UNIQUE_PER_PERIOD.contains(request.feeType())) {
            return Optional.empty();
        }
        return switch (request.target()) {
            case ChargeTarget.ProgramStudent t -> {
                Long id = t.enrollment().getId();
                Optional<Invoice> bySlot = request.feeType() == FeeType.ENROLLMENT_FEE && request.templateId() != null && request.slot() != null
                        ? invoiceRepository.findFirstByProgramEnrollmentIdAndFeeTypeAndTemplateIdAndEnrollmentFeeSlot(id, request.feeType(), request.templateId(), request.slot())
                        : Optional.empty();
                yield bySlot.or(() -> invoiceRepository.findFirstByProgramEnrollmentIdAndFeeTypeAndPeriod(id, request.feeType(), request.period()));
            }
            case ChargeTarget.CourseStudent t -> invoiceRepository.findFirstByCourseEnrollmentIdAndFeeTypeAndPeriod(t.enrollment().getId(), request.feeType(), request.period());
            default -> Optional.empty();
        };
    }
}
