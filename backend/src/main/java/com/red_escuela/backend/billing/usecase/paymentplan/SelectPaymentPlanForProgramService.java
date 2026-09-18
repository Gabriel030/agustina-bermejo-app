package com.red_escuela.backend.billing.usecase.paymentplan;

import com.red_escuela.backend.billing.dto.SelectPaymentPlanRequest;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.exception.PaymentPlanOptionMismatchException;
import com.red_escuela.backend.billing.repository.PaymentPlanSelectionRepository;
import com.red_escuela.backend.billing.service.PaymentPlanOptionFinder;
import com.red_escuela.backend.billing.service.PaymentPlanSelectionGuard;
import com.red_escuela.backend.billing.service.ProgramEnrollmentBillingScheduler;
import com.red_escuela.backend.billing.service.PromotionRedeemer;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.service.RecurringFeeWindowService;
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
    private final ProgramEnrollmentBillingScheduler programEnrollmentBillingScheduler;
    private final RecurringFeeWindowService recurringFeeWindowService;
    private final PromotionRedeemer promotionRedeemer;

    public SelectPaymentPlanForProgramService(PaymentPlanSelectionRepository paymentPlanSelectionRepository, PaymentPlanOptionFinder paymentPlanOptionFinder,
                                               ProgramEnrollmentRepository programEnrollmentRepository, CurrentPersonService currentPersonService,
                                               PaymentPlanSelectionGuard guard, ProgramEnrollmentBillingScheduler programEnrollmentBillingScheduler,
                                               RecurringFeeWindowService recurringFeeWindowService, PromotionRedeemer promotionRedeemer) {
        this.paymentPlanSelectionRepository = paymentPlanSelectionRepository;
        this.paymentPlanOptionFinder = paymentPlanOptionFinder;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.currentPersonService = currentPersonService;
        this.guard = guard;
        this.programEnrollmentBillingScheduler = programEnrollmentBillingScheduler;
        this.recurringFeeWindowService = recurringFeeWindowService;
        this.promotionRedeemer = promotionRedeemer;
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
        guard.validateUpfrontWindow(option);

        boolean isFirstSelection = paymentPlanSelectionRepository.findByProgramEnrollmentIdOrderByEffectiveFrom(programEnrollmentId).isEmpty();

        paymentPlanSelectionRepository.findByProgramEnrollmentIdAndEffectiveToIsNull(programEnrollmentId)
                .ifPresent(previous -> {
                    previous.setEffectiveTo(YearMonth.now().minusMonths(1));
                    paymentPlanSelectionRepository.saveAndFlush(previous);
                });

        PaymentPlanSelection selection = new PaymentPlanSelection();
        selection.setProgramEnrollment(programEnrollment);
        selection.setPaymentPlanOption(option);
        selection.setEffectiveFrom(YearMonth.now());
        if (option.getCadence() == PaymentPlanCadence.UPFRONT) {
            guard.validateScope(request.scopeMonths(), recurringFeeWindowService.remainingInvoices(programEnrollment));
            selection.setScopeMonths(request.scopeMonths());
        }
        selection.setCreatedBy(currentPersonService.resolveCurrentPerson());
        PaymentPlanSelection saved = paymentPlanSelectionRepository.save(selection);
        if (isFirstSelection) {
            promotionRedeemer.redeemIfEligible(programEnrollment, saved);
            programEnrollmentBillingScheduler.generateEnrollmentFee(programEnrollment, saved);
        }
        return saved;
    }
}
