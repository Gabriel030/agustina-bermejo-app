package com.red_escuela.backend.billing.usecase.paymentplan;

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
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.service.RecurringFeeWindowService;
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

    public SelectPaymentPlanForCourseService(PaymentPlanSelectionRepository paymentPlanSelectionRepository, PaymentPlanOptionFinder paymentPlanOptionFinder,
                                              CourseEnrollmentRepository courseEnrollmentRepository, CurrentPersonService currentPersonService,
                                              PaymentPlanSelectionGuard guard, RecurringFeeWindowService recurringFeeWindowService,
                                              PromotionRedeemer promotionRedeemer) {
        this.paymentPlanSelectionRepository = paymentPlanSelectionRepository;
        this.paymentPlanOptionFinder = paymentPlanOptionFinder;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.currentPersonService = currentPersonService;
        this.guard = guard;
        this.recurringFeeWindowService = recurringFeeWindowService;
        this.promotionRedeemer = promotionRedeemer;
    }

    @Override
    @Transactional
    public PaymentPlanSelection select(Long courseEnrollmentId, SelectPaymentPlanRequest request) {
        CourseEnrollment courseEnrollment = courseEnrollmentRepository.findById(courseEnrollmentId)
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(courseEnrollmentId));
        guard.validateOwnershipIfStudent(courseEnrollment.getStudent().getId(), courseEnrollmentId);
        // A course's payment plan is unique and immutable for the life of the enrollment.
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
        selection.setEffectiveFrom(YearMonth.now());
        if (option.getCadence() == PaymentPlanCadence.UPFRONT) {
            guard.validateScope(request.scopeMonths(), recurringFeeWindowService.remainingInvoices(courseEnrollment));
            selection.setScopeMonths(request.scopeMonths());
        }
        selection.setCreatedBy(currentPersonService.resolveCurrentPerson());
        PaymentPlanSelection saved = paymentPlanSelectionRepository.save(selection);
        // El plan de un curso es único e inmutable (chequeo de arriba) — toda selección de curso
        // es, por definición, la primera, así que siempre se evalúa contra promociones vigentes.
        promotionRedeemer.redeemIfEligible(courseEnrollment, saved);
        return saved;
    }
}
