package com.red_escuela.backend.billing.usecase.paymentplan;

import java.time.LocalDate;
import com.red_escuela.backend.billing.service.PaymentPlanSelectionInvoiceIssuingService;
import java.time.Clock;
import com.red_escuela.backend.billing.dto.SelectPaymentPlanRequest;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.exception.PaymentPlanOptionMismatchException;
import com.red_escuela.backend.billing.repository.PaymentPlanSelectionRepository;
import com.red_escuela.backend.billing.service.PaymentPlanOptionFinder;
import com.red_escuela.backend.billing.service.PaymentPlanSelectionGuard;
import com.red_escuela.backend.billing.service.ProgramEnrollmentBillingSetup;
import com.red_escuela.backend.billing.service.PromotionRedeemer;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

@UseCase
public class SelectPaymentPlanForProgramService implements SelectPaymentPlanForProgramUseCase {
    private final PaymentPlanSelectionRepository paymentPlanSelectionRepository;
    private final PaymentPlanOptionFinder paymentPlanOptionFinder;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CurrentPersonService currentPersonService;
    private final PaymentPlanSelectionGuard guard;
    private final ProgramEnrollmentBillingSetup programEnrollmentBillingSetup;
    private final PromotionRedeemer promotionRedeemer;
    private final PaymentPlanSelectionInvoiceIssuingService invoiceGenerationService;
    private final Clock clock;

    public SelectPaymentPlanForProgramService(PaymentPlanSelectionRepository paymentPlanSelectionRepository, PaymentPlanOptionFinder paymentPlanOptionFinder,
                                               ProgramEnrollmentRepository programEnrollmentRepository, CurrentPersonService currentPersonService,
                                               PaymentPlanSelectionGuard guard, ProgramEnrollmentBillingSetup programEnrollmentBillingSetup,
                                               PromotionRedeemer promotionRedeemer,
                                               PaymentPlanSelectionInvoiceIssuingService invoiceGenerationService,
                                               Clock clock) {
        this.invoiceGenerationService = invoiceGenerationService;
        this.paymentPlanSelectionRepository = paymentPlanSelectionRepository;
        this.paymentPlanOptionFinder = paymentPlanOptionFinder;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.currentPersonService = currentPersonService;
        this.guard = guard;
        this.programEnrollmentBillingSetup = programEnrollmentBillingSetup;
        this.promotionRedeemer = promotionRedeemer;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PaymentPlanSelection select(Long programEnrollmentId, SelectPaymentPlanRequest request) {
        ProgramEnrollment programEnrollment = programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
        guard.validateOwnershipIfStudent(programEnrollment.getStudent().getId(), programEnrollmentId);
        PaymentPlanOption option = paymentPlanOptionFinder.findOrThrow(request.paymentPlanOptionId());
        if (option.getProgram() == null || !option.getProgram().getId().equals(programEnrollment.getProgram().getId())) {
            throw new PaymentPlanOptionMismatchException(option.getId());
        }
        guard.requireAnnualUpfrontAvailableForProgram(option);

        boolean isFirstSelection = paymentPlanSelectionRepository.findByProgramEnrollmentIdOrderByEffectiveFrom(programEnrollmentId).isEmpty();

        paymentPlanSelectionRepository.findByProgramEnrollmentIdAndEffectiveToIsNull(programEnrollmentId)
                .ifPresent(previous -> {
                    previous.setEffectiveTo(YearMonth.now(clock).minusMonths(1));
                    paymentPlanSelectionRepository.saveAndFlush(previous);
                });

        PaymentPlanSelection selection = new PaymentPlanSelection();
        selection.setProgramEnrollment(programEnrollment);
        selection.setPaymentPlanOption(option);
        selection.setEffectiveFrom(YearMonth.now(clock));
        selection.setCreatedBy(currentPersonService.resolveCurrentPerson());
        PaymentPlanSelection saved = paymentPlanSelectionRepository.save(selection);
        if (isFirstSelection) {
            promotionRedeemer.redeemIfEligible(programEnrollment, saved);
            programEnrollmentBillingSetup.assignTemplateIfMissing(programEnrollment);
            invoiceGenerationService.issueInvoicesForNewPaymentPlan(programEnrollment, YearMonth.now(clock), LocalDate.now(clock));
        }
        return saved;
    }
}
