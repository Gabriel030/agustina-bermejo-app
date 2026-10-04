package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceAdjustmentType;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.billing.entity.Promotion;
import com.red_escuela.backend.billing.entity.PromotionBenefit;
import com.red_escuela.backend.billing.entity.PromotionRedemption;
import com.red_escuela.backend.billing.entity.Scholarship;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PromotionRedemptionRepository;
import com.red_escuela.backend.billing.repository.ScholarshipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import com.red_escuela.backend.academicstructure.entity.Course;

@Service
public class InvoiceFactory {
    private static final Logger log = LoggerFactory.getLogger(InvoiceFactory.class);
    private final PriceRevisionService priceRevisionService;
    private final InvoiceRepository invoiceRepository;
    private final ScholarshipRepository scholarshipRepository;
    private final PromotionRedemptionRepository promotionRedemptionRepository;

    public InvoiceFactory(PriceRevisionService priceRevisionService, InvoiceRepository invoiceRepository,
                           ScholarshipRepository scholarshipRepository, PromotionRedemptionRepository promotionRedemptionRepository) {
        this.priceRevisionService = priceRevisionService;
        this.invoiceRepository = invoiceRepository;
        this.scholarshipRepository = scholarshipRepository;
        this.promotionRedemptionRepository = promotionRedemptionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Invoice createForCourse(CourseEnrollment courseEnrollment, PaymentPlanSelection selection, FeeType feeType, YearMonth period, LocalDate dueDate) {
        Invoice invoice = saveIdempotently(buildForCourse(courseEnrollment, selection, feeType, period, dueDate));
        if (invoice != null) {
            applyActiveScholarshipIfPresent(invoice, scholarshipRepository.findActiveByCourseEnrollmentIdForUpdate(courseEnrollment.getId()));
            if (selection != null) {
                applyActivePromotionIfPresent(invoice, selection);
            }
        }
        return invoice;
    }

    private Invoice buildForCourse(CourseEnrollment courseEnrollment, PaymentPlanSelection selection, FeeType feeType, YearMonth period, LocalDate dueDate) {
        var price = priceRevisionService.resolveCurrentForCourse(courseEnrollment.getCourse().getId(), feeType, dueDate);
        Invoice invoice = new Invoice();
        invoice.setCourseEnrollment(courseEnrollment);
        invoice.setPaymentPlanSelection(selection);
        invoice.setFeeType(feeType);
        invoice.setPeriod(period);
        invoice.setDueDate(dueDate);
        invoice.setBaseAmount(price);
        invoice.setStatus(InvoiceStatus.PENDING);
        return invoice;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Invoice createForProgram(ProgramEnrollment programEnrollment, PaymentPlanSelection selection, FeeType feeType, YearMonth period, LocalDate dueDate) {
        Invoice invoice = saveIdempotently(buildForProgram(programEnrollment, selection, feeType, period, dueDate));
        if (invoice != null) {
            applyActiveScholarshipIfPresent(invoice, scholarshipRepository.findActiveByProgramEnrollmentIdForUpdate(programEnrollment.getId()));
            if (selection != null) {
                applyActivePromotionIfPresent(invoice, selection);
            }
        }
        return invoice;
    }

    @Transactional
    public Invoice createForProgramAtomic(ProgramEnrollment programEnrollment, PaymentPlanSelection selection, FeeType feeType, YearMonth period, LocalDate dueDate) {
        Invoice invoice = invoiceRepository.saveAndFlush(buildForProgram(programEnrollment, selection, feeType, period, dueDate));
        applyActiveScholarshipIfPresent(invoice, scholarshipRepository.findActiveByProgramEnrollmentIdForUpdate(programEnrollment.getId()));
        if (selection != null) {
            applyActivePromotionIfPresent(invoice, selection);
        }
        return invoice;
    }

    private Invoice buildForProgram(ProgramEnrollment programEnrollment, PaymentPlanSelection selection, FeeType feeType, YearMonth period, LocalDate dueDate) {
        var price = priceRevisionService.resolveCurrentForProgram(programEnrollment.getProgram().getId(), feeType, dueDate);
        Invoice invoice = new Invoice();
        invoice.setProgramEnrollment(programEnrollment);
        invoice.setPaymentPlanSelection(selection);
        invoice.setFeeType(feeType);
        invoice.setPeriod(period);
        invoice.setDueDate(dueDate);
        invoice.setBaseAmount(price);
        invoice.setStatus(InvoiceStatus.PENDING);
        return invoice;
    }

    private void applyActiveScholarshipIfPresent(Invoice invoice, java.util.Optional<Scholarship> activeScholarship) {
        activeScholarship.ifPresent(scholarship -> {
            scholarship.consumeOne(invoice);
            invoice.applyAdjustment(InvoiceAdjustmentType.SCHOLARSHIP, scholarship.getValue(), scholarship.getReason(), null);
            scholarshipRepository.save(scholarship);
            invoiceRepository.save(invoice);
        });
    }

    private void applyActivePromotionIfPresent(Invoice invoice, PaymentPlanSelection selection) {
        promotionRedemptionRepository.findByPaymentPlanSelectionIdForUpdate(selection.getId()).ifPresent(redemption -> {
            Promotion promotion = redemption.getPromotion();
            for (PromotionBenefit benefit : promotion.getBenefits()) {
                if (benefit.getFeeType() != invoice.getFeeType()) {
                    continue;
                }
                long alreadyApplied = invoiceRepository.countByPaymentPlanSelectionAndFeeTypeAndPromotionBenefit(
                        selection.getId(), invoice.getFeeType(), benefit.getId());
                if (benefit.getInvoiceScopeCount() == null || alreadyApplied < benefit.getInvoiceScopeCount()) {
                    invoice.applyAdjustment(InvoiceAdjustmentType.PROMOTION, benefit.getValue(), "Promoción: " + promotion.getDescription(), null, benefit);
                }
            }
            invoiceRepository.save(invoice);
        });
    }

    @Transactional
    public Invoice createAdHocForProgram(Program program, FeeType feeType, YearMonth period, LocalDate dueDate) {
        var price = priceRevisionService.resolveCurrentForProgram(program.getId(), feeType, dueDate);
        return saveAdHoc(feeType, period, dueDate, price);
    }

    @Transactional
    public Invoice createAdHocForCourse(Course course, FeeType feeType, YearMonth period, LocalDate dueDate) {
        var price = priceRevisionService.resolveCurrentForCourse(course.getId(), feeType, dueDate);
        return saveAdHoc(feeType, period, dueDate, price);
    }

    private Invoice saveAdHoc(FeeType feeType, YearMonth period, LocalDate dueDate, Money price) {
        Invoice invoice = new Invoice();
        invoice.setFeeType(feeType);
        invoice.setPeriod(period);
        invoice.setDueDate(dueDate);
        invoice.setBaseAmount(price);
        invoice.setStatus(InvoiceStatus.PENDING);
        return invoiceRepository.saveAndFlush(invoice);
    }

    @Transactional
    public Invoice createAdHocFeeForCourseEnrollment(CourseEnrollment courseEnrollment, FeeType feeType, Money amount,
                                                       LocalDate dueDate, String description, Subject subject, ExamBoardRegistration examBoardRegistration) {
        Invoice invoice = new Invoice();
        invoice.setCourseEnrollment(courseEnrollment);
        invoice.setFeeType(feeType);
        invoice.setPeriod(YearMonth.from(dueDate));
        invoice.setDueDate(dueDate);
        invoice.setBaseAmount(amount);
        invoice.setDescription(description);
        invoice.setSubject(subject);
        invoice.setExamBoardRegistration(examBoardRegistration);
        invoice.setStatus(InvoiceStatus.PENDING);
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice createAdHocFeeForProgramEnrollment(ProgramEnrollment programEnrollment, FeeType feeType, Money amount,
                                                        LocalDate dueDate, String description, Subject subject, ExamBoardRegistration examBoardRegistration) {
        Invoice invoice = new Invoice();
        invoice.setProgramEnrollment(programEnrollment);
        invoice.setFeeType(feeType);
        invoice.setPeriod(YearMonth.from(dueDate));
        invoice.setDueDate(dueDate);
        invoice.setBaseAmount(amount);
        invoice.setDescription(description);
        invoice.setSubject(subject);
        invoice.setExamBoardRegistration(examBoardRegistration);
        invoice.setStatus(InvoiceStatus.PENDING);
        return invoiceRepository.save(invoice);
    }

    private Invoice saveIdempotently(Invoice invoice) {
        try {
            Invoice saved = invoiceRepository.saveAndFlush(invoice);
            log.info("Factura generada: factura={}, tipo={}, periodo={}, vencimiento={}",
                    saved.getId(), saved.getFeeType(), saved.getPeriod(), saved.getDueDate());
            return saved;
        } catch (DataIntegrityViolationException e) {
            log.warn("Factura omitida por duplicada: tipo={}, periodo={}", invoice.getFeeType(), invoice.getPeriod());
            org.springframework.transaction.interceptor.TransactionAspectSupport
                    .currentTransactionStatus().setRollbackOnly();
            return null;
        }
    }
}
