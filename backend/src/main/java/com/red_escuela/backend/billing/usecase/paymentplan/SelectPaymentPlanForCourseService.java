package com.red_escuela.backend.billing.usecase.paymentplan;

import java.time.Clock;
import com.red_escuela.backend.billing.dto.SelectPaymentPlanRequest;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.exception.CoursePaymentPlanAlreadySetException;
import com.red_escuela.backend.billing.exception.PaymentPlanOptionMismatchException;
import com.red_escuela.backend.billing.repository.PaymentPlanSelectionRepository;
import com.red_escuela.backend.billing.service.PaymentPlanOptionFinder;
import com.red_escuela.backend.billing.service.PaymentPlanSelectionGuard;
import com.red_escuela.backend.billing.service.PromotionRedeemer;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.billing.service.RecurringFeeWindowService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

@UseCase
public class SelectPaymentPlanForCourseService implements SelectPaymentPlanForCourseUseCase {
    private final PaymentPlanSelectionRepository paymentPlanSelectionRepository;
    private final PaymentPlanOptionFinder paymentPlanOptionFinder;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final CurrentPersonService currentPersonService;
    private final PaymentPlanSelectionGuard guard;
    private final RecurringFeeWindowService recurringFeeWindowService;
    private final PromotionRedeemer promotionRedeemer;
    private final Clock clock;

    public SelectPaymentPlanForCourseService(PaymentPlanSelectionRepository paymentPlanSelectionRepository, PaymentPlanOptionFinder paymentPlanOptionFinder,
                                              CourseEnrollmentRepository courseEnrollmentRepository, CurrentPersonService currentPersonService,
                                              PaymentPlanSelectionGuard guard, RecurringFeeWindowService recurringFeeWindowService,
                                              PromotionRedeemer promotionRedeemer,
                                              Clock clock) {
        this.paymentPlanSelectionRepository = paymentPlanSelectionRepository;
        this.paymentPlanOptionFinder = paymentPlanOptionFinder;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.currentPersonService = currentPersonService;
        this.guard = guard;
        this.recurringFeeWindowService = recurringFeeWindowService;
        this.promotionRedeemer = promotionRedeemer;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PaymentPlanSelection select(Long courseEnrollmentId, SelectPaymentPlanRequest request) {
        CourseEnrollment courseEnrollment = courseEnrollmentRepository.findById(courseEnrollmentId)
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(courseEnrollmentId));
        guard.validateOwnershipIfStudent(courseEnrollment.getStudent().getId(), courseEnrollmentId);
        if (paymentPlanSelectionRepository.findByCourseEnrollmentIdAndEffectiveToIsNull(courseEnrollmentId).isPresent()) {
            throw new CoursePaymentPlanAlreadySetException(courseEnrollmentId);
        }
        PaymentPlanOption option = paymentPlanOptionFinder.findOrThrow(request.paymentPlanOptionId());
        if (option.getCourse() == null || !option.getCourse().getId().equals(courseEnrollment.getCourse().getId())) {
            throw new PaymentPlanOptionMismatchException(option.getId());
        }
        guard.validateUpfrontWindow(option);

        PaymentPlanSelection selection = new PaymentPlanSelection();
        selection.setCourseEnrollment(courseEnrollment);
        selection.setPaymentPlanOption(option);
        selection.setEffectiveFrom(YearMonth.now(clock));
        if (option.getCadence() == PaymentPlanCadence.ANNUAL_UPFRONT) {
            guard.validateScope(request.scopeMonths(), recurringFeeWindowService.remainingInvoices(courseEnrollment));
            selection.setScopeMonths(request.scopeMonths());
        }
        selection.setCreatedBy(currentPersonService.resolveCurrentPerson());
        PaymentPlanSelection saved = paymentPlanSelectionRepository.save(selection);
        promotionRedeemer.redeemIfEligible(courseEnrollment, saved);
        return saved;
    }
}
