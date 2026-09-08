package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.dto.CreatePromotionBenefitRequest;
import com.red_escuela.backend.billing.dto.CreatePromotionRequest;
import com.red_escuela.backend.billing.dto.LostPromotionBenefitResponse;
import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.billing.entity.Promotion;
import com.red_escuela.backend.billing.entity.PromotionBenefit;
import com.red_escuela.backend.billing.entity.PromotionRedemption;
import com.red_escuela.backend.billing.entity.PromotionStatus;
import com.red_escuela.backend.exception.CourseNotFoundException;
import com.red_escuela.backend.billing.exception.DuplicatePromotionBenefitFeeTypeException;
import com.red_escuela.backend.exception.InvalidWindowConfigurationException;
import com.red_escuela.backend.exception.ProgramNotFoundException;
import com.red_escuela.backend.billing.exception.PromotionAlreadyClosedException;
import com.red_escuela.backend.billing.exception.PromotionNotFoundException;
import com.red_escuela.backend.repository.CourseRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.repository.ProgramRepository;
import com.red_escuela.backend.billing.repository.PromotionRedemptionRepository;
import com.red_escuela.backend.billing.repository.PromotionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import com.red_escuela.backend.service.CurrentPersonService;

@Service
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final PromotionRedemptionRepository promotionRedemptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final CourseRepository courseRepository;
    private final ProgramRepository programRepository;
    private final CurrentPersonService currentPersonService;

    public PromotionService(PromotionRepository promotionRepository, PromotionRedemptionRepository promotionRedemptionRepository,
                             InvoiceRepository invoiceRepository, CourseRepository courseRepository, ProgramRepository programRepository,
                             CurrentPersonService currentPersonService) {
        this.promotionRepository = promotionRepository;
        this.promotionRedemptionRepository = promotionRedemptionRepository;
        this.invoiceRepository = invoiceRepository;
        this.courseRepository = courseRepository;
        this.programRepository = programRepository;
        this.currentPersonService = currentPersonService;
    }

    @Transactional
    public Promotion createForCourse(Long courseId, CreatePromotionRequest request) {
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new CourseNotFoundException(courseId));
        Promotion promotion = build(request);
        promotion.setCourse(course);
        return promotionRepository.save(promotion);
    }

    @Transactional
    public Promotion createForProgram(Long programId, CreatePromotionRequest request) {
        Program program = programRepository.findById(programId).orElseThrow(() -> new ProgramNotFoundException(programId));
        Promotion promotion = build(request);
        promotion.setProgram(program);
        return promotionRepository.save(promotion);
    }

    @Transactional
    public void close(Long id) {
        Promotion promotion = promotionRepository.findById(id).orElseThrow(() -> new PromotionNotFoundException(id));
        if (promotion.getStatus() == PromotionStatus.CLOSED) {
            throw new PromotionAlreadyClosedException(id);
        }
        promotion.close(currentPersonService.resolveCurrentPerson());
        promotionRepository.save(promotion);
    }

    @Transactional(readOnly = true)
    public List<Promotion> findActive() {
        return promotionRepository.findByStatus(PromotionStatus.ACTIVE);
    }

    // Solo la primera selección de un enrollment puede calificar (PaymentPlanSelectionService
    // decide cuándo llamar esto) — acá solo se resuelve si hay una Promotion vigente para el curso
    // en la fecha de matriculación, y se registra la redención si la hay.
    @Transactional
    public void redeemIfEligible(CourseEnrollment courseEnrollment, PaymentPlanSelection selection) {
        promotionRepository.findFirstActiveByCourseAndDateInWindow(courseEnrollment.getCourse().getId(), courseEnrollment.getEnrollmentDate().toLocalDate())
                .ifPresent(promotion -> redeem(promotion, selection));
    }

    @Transactional
    public void redeemIfEligible(ProgramEnrollment programEnrollment, PaymentPlanSelection selection) {
        promotionRepository.findFirstActiveByProgramAndDateInWindow(programEnrollment.getProgram().getId(), programEnrollment.getEnrollmentDate().toLocalDate())
                .ifPresent(promotion -> redeem(promotion, selection));
    }

    private void redeem(Promotion promotion, PaymentPlanSelection selection) {
        PromotionRedemption redemption = new PromotionRedemption();
        redemption.setPromotion(promotion);
        redemption.setPaymentPlanSelection(selection);
        promotionRedemptionRepository.save(redemption);
    }

    // Reporte de beneficios cortados por cambio de plan — cada PromotionRedemption cuya selección
    // ya se cerró (effectiveTo != null) y todavía tenía cuotas de descuento sin usar cuando se
    // cortó. No se persiste como estado propio: se recalcula siempre contra la realidad de
    // InvoiceAdjustment, mismo criterio que getAmountDue().
    @Transactional(readOnly = true)
    public List<LostPromotionBenefitResponse> findLostBenefits() {
        return promotionRedemptionRepository.findAll().stream()
                .filter(redemption -> redemption.getPaymentPlanSelection().getEffectiveTo() != null)
                .flatMap(redemption -> lostBenefitsOf(redemption).stream())
                .toList();
    }

    private List<LostPromotionBenefitResponse> lostBenefitsOf(PromotionRedemption redemption) {
        PaymentPlanSelection selection = redemption.getPaymentPlanSelection();
        String studentName = resolveStudentName(selection);
        return redemption.getPromotion().getBenefits().stream()
                .map(benefit -> toLostBenefit(redemption, selection, benefit, studentName))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    private Optional<LostPromotionBenefitResponse> toLostBenefit(PromotionRedemption redemption, PaymentPlanSelection selection, PromotionBenefit benefit, String studentName) {
        if (benefit.getInvoiceScopeCount() == null) {
            return Optional.empty();
        }
        long used = invoiceRepository.countByPaymentPlanSelectionAndFeeTypeAndPromotionBenefit(selection.getId(), benefit.getFeeType(), benefit.getId());
        int remaining = (int) (benefit.getInvoiceScopeCount() - used);
        if (remaining <= 0) {
            return Optional.empty();
        }
        return Optional.of(new LostPromotionBenefitResponse(
                redemption.getId(), redemption.getPromotion().getDescription(), studentName, benefit.getFeeType().name(),
                (int) used, benefit.getInvoiceScopeCount(), remaining, selection.getEffectiveTo()));
    }

    private String resolveStudentName(PaymentPlanSelection selection) {
        Person person = selection.getCourseEnrollment() != null
                ? selection.getCourseEnrollment().getStudent().getPerson()
                : selection.getProgramEnrollment().getStudent().getPerson();
        return person.getName() + " " + person.getLastname();
    }

    private Promotion build(CreatePromotionRequest request) {
        if (request.eligibilityValidFrom().isAfter(request.eligibilityValidTo())) {
            throw new InvalidWindowConfigurationException("eligibilityValidFrom no puede ser posterior a eligibilityValidTo");
        }
        Promotion promotion = new Promotion();
        promotion.setDescription(request.description());
        promotion.setEligibilityValidFrom(request.eligibilityValidFrom());
        promotion.setEligibilityValidTo(request.eligibilityValidTo());
        promotion.setCreatedBy(currentPersonService.resolveCurrentPerson());
        java.util.Set<FeeType> feeTypesYaUsados = new java.util.HashSet<>();
        for (CreatePromotionBenefitRequest benefitRequest : request.benefits()) {
            if (!feeTypesYaUsados.add(benefitRequest.feeType())) {
                throw new DuplicatePromotionBenefitFeeTypeException(benefitRequest.feeType().name());
            }
            PromotionBenefit benefit = new PromotionBenefit();
            benefit.setPromotion(promotion);
            benefit.setFeeType(benefitRequest.feeType());
            benefit.setValue(benefitRequest.value().toValue());
            benefit.setInvoiceScopeCount(benefitRequest.invoiceScopeCount());
            promotion.getBenefits().add(benefit);
        }
        return promotion;
    }
}
