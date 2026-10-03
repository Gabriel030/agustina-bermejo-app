package com.red_escuela.backend.config.demo;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.entity.BillingPeriod;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.CollectionsPolicy;
import com.red_escuela.backend.billing.entity.CreditNote;
import com.red_escuela.backend.billing.entity.CreditNoteReasonType;
import com.red_escuela.backend.billing.entity.EnrollmentBillingStanding;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceAdjustmentType;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.MonetaryAdjustmentValue;
import com.red_escuela.backend.billing.entity.Payment;
import com.red_escuela.backend.billing.entity.PaymentGatewayConfig;
import com.red_escuela.backend.billing.entity.PaymentMethod;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.entity.PaymentReceipt;
import com.red_escuela.backend.billing.entity.PaymentReceiptStatus;
import com.red_escuela.backend.billing.entity.PaymentStatus;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.billing.entity.Promotion;
import com.red_escuela.backend.billing.entity.PromotionBenefit;
import com.red_escuela.backend.billing.entity.Scholarship;
import com.red_escuela.backend.billing.repository.BillingPeriodRepository;
import com.red_escuela.backend.billing.repository.CollectionsPolicyRepository;
import com.red_escuela.backend.billing.repository.CreditNoteRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PaymentGatewayConfigRepository;
import com.red_escuela.backend.billing.repository.PaymentPlanOptionRepository;
import com.red_escuela.backend.billing.repository.PaymentPlanSelectionRepository;
import com.red_escuela.backend.billing.repository.PaymentReceiptRepository;
import com.red_escuela.backend.billing.repository.PaymentRepository;
import com.red_escuela.backend.billing.repository.ProgramCohortRepository;
import com.red_escuela.backend.billing.repository.PriceRevisionRepository;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.billing.repository.PromotionRepository;
import com.red_escuela.backend.billing.repository.ScholarshipRepository;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Person;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile("local & demo")
public class DemoBillingSeeder {

    private enum BillingProfile {
        UP_TO_DATE, ONE_OVERDUE, SUSPENDED, UPFRONT, SCHOLARSHIP, RECEIPT_PENDING, RECEIPT_REJECTED, MERCADO_PAGO, PROMOTION
    }

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);
    private static final YearMonth CURRENT_PERIOD = YearMonth.of(2026, 10);
    private static final BigDecimal SURCHARGE = new BigDecimal("0.10");

    private final CollectionsPolicyRepository collectionsPolicyRepository;
    private final BillingPeriodRepository billingPeriodRepository;
    private final PaymentGatewayConfigRepository paymentGatewayConfigRepository;
    private final PaymentPlanOptionRepository paymentPlanOptionRepository;
    private final PaymentPlanSelectionRepository paymentPlanSelectionRepository;
    private final ProgramYearBillingTemplateRepository billingTemplateRepository;
    private final ProgramCohortRepository cohortRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentReceiptRepository paymentReceiptRepository;
    private final ScholarshipRepository scholarshipRepository;
    private final PromotionRepository promotionRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final PriceRevisionRepository priceRevisionRepository;

    public DemoBillingSeeder(
            CollectionsPolicyRepository collectionsPolicyRepository,
            BillingPeriodRepository billingPeriodRepository,
            PaymentGatewayConfigRepository paymentGatewayConfigRepository,
            PaymentPlanOptionRepository paymentPlanOptionRepository,
            PaymentPlanSelectionRepository paymentPlanSelectionRepository,
            ProgramYearBillingTemplateRepository billingTemplateRepository,
            ProgramCohortRepository cohortRepository,
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository,
            PaymentReceiptRepository paymentReceiptRepository,
            ScholarshipRepository scholarshipRepository,
            PromotionRepository promotionRepository,
            CreditNoteRepository creditNoteRepository,
            ProgramEnrollmentRepository programEnrollmentRepository,
            CourseEnrollmentRepository courseEnrollmentRepository,
            PriceRevisionRepository priceRevisionRepository) {
        this.collectionsPolicyRepository = collectionsPolicyRepository;
        this.billingPeriodRepository = billingPeriodRepository;
        this.paymentGatewayConfigRepository = paymentGatewayConfigRepository;
        this.paymentPlanOptionRepository = paymentPlanOptionRepository;
        this.paymentPlanSelectionRepository = paymentPlanSelectionRepository;
        this.billingTemplateRepository = billingTemplateRepository;
        this.cohortRepository = cohortRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.paymentReceiptRepository = paymentReceiptRepository;
        this.scholarshipRepository = scholarshipRepository;
        this.promotionRepository = promotionRepository;
        this.creditNoteRepository = creditNoteRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.priceRevisionRepository = priceRevisionRepository;
    }

    public void seed(DemoDataset dataset) {
        createInstitutionalConfig(dataset);
        createPlanOptionsAndTemplates(dataset);
        createPriceRevisions(dataset);
        createOpenCohorts(dataset);
        createPromotions(dataset);
        billProgramEnrollments(dataset);
        billCourseEnrollments(dataset);
        createCreditNotes(dataset);
    }

    private void createInstitutionalConfig(DemoDataset dataset) {
        CollectionsPolicy policy = new CollectionsPolicy();
        policy.setGracePeriodDays(10);
        policy.setSurchargePercentage(SURCHARGE);
        policy.setSuspensionAfterDays(60);
        policy.setSuspensionAfterInvoiceCount(2);
        policy.setWithdrawalAfterDays(120);
        policy.setWithdrawalAfterInvoiceCount(4);
        policy.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        policy.setCreatedBy(dataset.getDirector());
        collectionsPolicyRepository.save(policy);

        BillingPeriod period = new BillingPeriod();
        period.setStartDay(1);
        period.setEndDay(10);
        period.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        period.setCreatedBy(dataset.getDirector());
        billingPeriodRepository.save(period);

        PaymentGatewayConfig gateway = paymentGatewayConfigRepository.findAll().stream()
                .min(java.util.Comparator.comparing(PaymentGatewayConfig::getId))
                .orElseGet(PaymentGatewayConfig::new);
        gateway.setBankAlias("isab.pagos.cba");
        gateway.setBankCbu("0070123430004567890123");
        gateway.setAccountHolder("Instituto Superior Agustina Bermejo");
        gateway.setMpEnabled(true);
        gateway.setUpdatedAt(LocalDateTime.of(2026, 2, 10, 11, 0));
        gateway.setUpdatedBy(dataset.administrativeAt(0));
        paymentGatewayConfigRepository.save(gateway);
    }

    private void createPlanOptionsAndTemplates(DemoDataset dataset) {
        Person director = dataset.getDirector();
        for (Program program : dataset.getPrograms().values()) {
            savePlanOption(program, null, "Cuota mensual", PaymentPlanCadence.MONTHLY, null, director);
            savePlanOption(program, null, "Pago anual anticipado — 15% de descuento", PaymentPlanCadence.UPFRONT,
                    new MonetaryAdjustmentValue.PercentageAmount(new BigDecimal("0.15")), director);

            ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
            template.setProgram(program);
            template.setTerm(dataset.term(2026));
            template.setRecurringFeeInvoicesCount(10);
            template.setCreatedBy(director);
            ProgramYearBillingTemplate savedTemplate = billingTemplateRepository.save(template);

            ProgramCohort cohort = new ProgramCohort();
            cohort.setTemplate(savedTemplate);
            cohort.setIntake(CohortIntake.FIRST);
            cohort.setAdmissionStart(LocalDate.of(2026, 1, 5));
            cohort.setAdmissionEnd(LocalDate.of(2026, 3, 31));
            cohort.setCursadaStart(LocalDate.of(2026, 3, 2));
            cohort.setCreatedBy(director);
            cohortRepository.save(cohort);
        }
        for (Course course : dataset.getCourses().values()) {
            savePlanOption(null, course, "Cuota mensual", PaymentPlanCadence.MONTHLY, null, director);
            savePlanOption(null, course, "Pago total anticipado — 10% de descuento", PaymentPlanCadence.UPFRONT,
                    new MonetaryAdjustmentValue.PercentageAmount(new BigDecimal("0.10")), director);
        }
    }

    private void createPriceRevisions(DemoDataset dataset) {
        Person director = dataset.getDirector();
        LocalDate effectiveFrom = LocalDate.of(2024, 1, 1);
        for (DemoCatalog.ProgramSpec spec : DemoCatalog.PROGRAMS) {
            Program program = dataset.program(spec.name());
            savePriceRevision(program, null, FeeType.RECURRING_FEE, "Cuota mensual", spec.monthlyPrice(), effectiveFrom, director);
            savePriceRevision(program, null, FeeType.ENROLLMENT_FEE, "Matrícula anual", spec.enrollmentFee(), effectiveFrom, director);
        }
        for (DemoCatalog.CourseSpec spec : DemoCatalog.COURSES) {
            savePriceRevision(null, dataset.course(spec.name()), FeeType.RECURRING_FEE, "Cuota mensual", spec.price(), effectiveFrom, director);
        }
    }

    private void savePriceRevision(
            Program program, Course course, FeeType feeType, String description,
            BigDecimal amount, LocalDate effectiveFrom, Person createdBy) {
        PriceRevision revision = new PriceRevision();
        revision.setProgram(program);
        revision.setCourse(course);
        revision.setFeeType(feeType);
        revision.setDescription(description);
        revision.setMonthlyAmount(amount);
        revision.setEffectiveFrom(effectiveFrom);
        revision.setCreatedBy(createdBy);
        priceRevisionRepository.save(revision);
    }

    private void createOpenCohorts(DemoDataset dataset) {
        Person director = dataset.getDirector();
        for (Program program : dataset.getPrograms().values()) {
            ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
            template.setProgram(program);
            template.setTerm(dataset.term(2027));
            template.setRecurringFeeInvoicesCount(10);
            template.setCreatedBy(director);
            ProgramYearBillingTemplate savedTemplate = billingTemplateRepository.save(template);

            ProgramCohort cohort = new ProgramCohort();
            cohort.setTemplate(savedTemplate);
            cohort.setIntake(CohortIntake.FIRST);
            cohort.setAdmissionStart(LocalDate.of(2026, 9, 1));
            cohort.setAdmissionEnd(LocalDate.of(2027, 2, 26));
            cohort.setCursadaStart(LocalDate.of(2027, 3, 1));
            cohort.setCreatedBy(director);
            cohortRepository.save(cohort);
        }
    }

    private PaymentPlanOption savePlanOption(
            Program program, Course course, String description, PaymentPlanCadence cadence,
            MonetaryAdjustmentValue discount, Person createdBy) {
        PaymentPlanOption option = new PaymentPlanOption();
        option.setProgram(program);
        option.setCourse(course);
        option.setDescription(description);
        option.setCadence(cadence);
        option.setUpfrontDiscount(discount);
        option.setUpfrontWindowEnabled(cadence == PaymentPlanCadence.UPFRONT);
        if (cadence == PaymentPlanCadence.UPFRONT) {
            option.setUpfrontWindowStart(LocalDate.of(2026, 1, 5));
            option.setUpfrontWindowEnd(LocalDate.of(2026, 3, 31));
        }
        option.setCreatedBy(createdBy);
        return paymentPlanOptionRepository.save(option);
    }

    private void createPromotions(DemoDataset dataset) {
        Promotion promotion = new Promotion();
        promotion.setDescription("Matrícula bonificada 20% — inscripción temprana 2026");
        promotion.setProgram(dataset.program(DemoCatalog.SYSTEMS_ANALYSIS));
        promotion.setEligibilityValidFrom(LocalDate.of(2026, 1, 5));
        promotion.setEligibilityValidTo(LocalDate.of(2026, 2, 28));
        promotion.setCreatedBy(dataset.getDirector());
        PromotionBenefit benefit = new PromotionBenefit();
        benefit.setPromotion(promotion);
        benefit.setFeeType(FeeType.ENROLLMENT_FEE);
        benefit.setValue(new MonetaryAdjustmentValue.PercentageAmount(new BigDecimal("0.20")));
        benefit.setInvoiceScopeCount(1);
        promotion.getBenefits().add(benefit);
        promotionRepository.save(promotion);

        Promotion coursePromotion = new Promotion();
        coursePromotion.setDescription("2x1 en cursos cortos — campaña de invierno");
        coursePromotion.setCourse(dataset.course("Primeros Auxilios y RCP"));
        coursePromotion.setEligibilityValidFrom(LocalDate.of(2026, 6, 1));
        coursePromotion.setEligibilityValidTo(LocalDate.of(2026, 7, 31));
        coursePromotion.setCreatedBy(dataset.getDirector());
        PromotionBenefit courseBenefit = new PromotionBenefit();
        courseBenefit.setPromotion(coursePromotion);
        courseBenefit.setFeeType(FeeType.RECURRING_FEE);
        courseBenefit.setValue(new MonetaryAdjustmentValue.PercentageAmount(new BigDecimal("0.50")));
        courseBenefit.setInvoiceScopeCount(2);
        coursePromotion.getBenefits().add(courseBenefit);
        promotionRepository.save(coursePromotion);
    }

    private void billProgramEnrollments(DemoDataset dataset) {
        List<ProgramEnrollment> enrollments = dataset.getProgramEnrollments();
        for (int i = 0; i < enrollments.size(); i++) {
            ProgramEnrollment enrollment = enrollments.get(i);
            if (enrollment.getStatus() == ProgramEnrollmentStatus.WITHDRAWN) {
                continue;
            }
            BillingProfile profile = enrollment.getStudent().getId().equals(dataset.getDemoStudent().getId())
                    ? BillingProfile.ONE_OVERDUE
                    : BillingProfile.values()[i % BillingProfile.values().length];
            Program program = enrollment.getProgram();
            BigDecimal monthly = program.getPrice();
            BigDecimal enrollmentFee = program.getEnrollmentFeeAmount();
            int startYear = enrollment.getEnrollmentDate().getYear();

            selectPlan(dataset, enrollment, null, profile);
            Scholarship scholarship = profile == BillingProfile.SCHOLARSHIP
                    ? createScholarship(dataset, enrollment)
                    : null;

            for (int year = startYear; year <= 2026; year++) {
                boolean closedYear = year < 2026;
                Invoice fee = buildInvoice(enrollment, null, FeeType.ENROLLMENT_FEE, YearMonth.of(year, 2),
                        LocalDate.of(year, 2, 28), enrollmentFee, "Matrícula " + year);
                if (profile == BillingProfile.PROMOTION && year == 2026) {
                    fee.applyAdjustment(InvoiceAdjustmentType.PROMOTION,
                            new MonetaryAdjustmentValue.PercentageAmount(new BigDecimal("0.20")),
                            "Promoción de inscripción temprana", dataset.administrativeAt(0));
                }
                settle(dataset, fee, PaymentMethod.TRANSFER, LocalDate.of(year, 2, 20));

                for (int month = 3; month <= (closedYear ? 12 : 10); month++) {
                    YearMonth period = YearMonth.of(year, month);
                    Invoice invoice = buildInvoice(enrollment, null, FeeType.RECURRING_FEE, period,
                            LocalDate.of(year, month, 10), monthly,
                            "Cuota " + String.format("%02d/%d", month, year));
                    if (scholarship != null) {
                        invoice.applyAdjustment(InvoiceAdjustmentType.SCHOLARSHIP,
                                new MonetaryAdjustmentValue.PercentageAmount(new BigDecimal("0.50")),
                                "Beca socioeconómica 50%", dataset.getDirector());
                        scholarship.consumeOne(invoice);
                    }
                    applyCurrentYearProfile(dataset, invoice, profile, closedYear, period);
                }
            }
            updateStanding(enrollment, profile);
        }
    }

    private void applyCurrentYearProfile(
            DemoDataset dataset, Invoice invoice, BillingProfile profile, boolean closedYear, YearMonth period) {
        if (closedYear) {
            settle(dataset, invoice, PaymentMethod.CASH, invoice.getDueDate().minusDays(3));
            return;
        }
        boolean isCurrentPeriod = period.equals(CURRENT_PERIOD);
        switch (profile) {
            case UP_TO_DATE, PROMOTION -> {
                if (!isCurrentPeriod) {
                    settle(dataset, invoice, PaymentMethod.TRANSFER, invoice.getDueDate().minusDays(2));
                } else {
                    invoiceRepository.save(invoice);
                }
            }
            case ONE_OVERDUE -> {
                if (period.getMonthValue() <= 8) {
                    settle(dataset, invoice, PaymentMethod.TRANSFER, invoice.getDueDate().minusDays(1));
                } else if (period.getMonthValue() == 9) {
                    invoice.markOverdue(new MonetaryAdjustmentValue.PercentageAmount(SURCHARGE), "Recargo por mora");
                    invoiceRepository.save(invoice);
                } else {
                    invoiceRepository.save(invoice);
                }
            }
            case SUSPENDED -> {
                if (period.getMonthValue() <= 6) {
                    settle(dataset, invoice, PaymentMethod.CASH, invoice.getDueDate().minusDays(4));
                } else if (!isCurrentPeriod) {
                    invoice.markOverdue(new MonetaryAdjustmentValue.PercentageAmount(SURCHARGE), "Recargo por mora");
                    invoiceRepository.save(invoice);
                } else {
                    invoiceRepository.save(invoice);
                }
            }
            case UPFRONT -> {
                invoice.applyAdjustment(InvoiceAdjustmentType.DISCOUNT,
                        new MonetaryAdjustmentValue.PercentageAmount(new BigDecimal("0.15")),
                        "Descuento por pago anual anticipado", dataset.administrativeAt(1));
                settle(dataset, invoice, PaymentMethod.TRANSFER, LocalDate.of(2026, 3, 5));
            }
            case SCHOLARSHIP -> {
                if (!isCurrentPeriod) {
                    settle(dataset, invoice, PaymentMethod.CASH, invoice.getDueDate().minusDays(5));
                } else {
                    invoiceRepository.save(invoice);
                }
            }
            case RECEIPT_PENDING -> {
                if (period.getMonthValue() <= 8) {
                    settle(dataset, invoice, PaymentMethod.TRANSFER, invoice.getDueDate().minusDays(2));
                } else if (period.getMonthValue() == 9) {
                    Invoice saved = invoiceRepository.save(invoice);
                    createReceipt(dataset, saved, PaymentReceiptStatus.PENDING_REVIEW, null);
                } else {
                    invoiceRepository.save(invoice);
                }
            }
            case RECEIPT_REJECTED -> {
                if (period.getMonthValue() <= 7) {
                    settle(dataset, invoice, PaymentMethod.TRANSFER, invoice.getDueDate().minusDays(2));
                } else if (period.getMonthValue() == 8) {
                    Invoice saved = invoiceRepository.save(invoice);
                    createReceipt(dataset, saved, PaymentReceiptStatus.REJECTED,
                            "El comprobante corresponde a otro período");
                } else {
                    invoiceRepository.save(invoice);
                }
            }
            case MERCADO_PAGO -> {
                if (!isCurrentPeriod) {
                    settle(dataset, invoice, PaymentMethod.MERCADO_PAGO, invoice.getDueDate().minusDays(6));
                } else {
                    invoiceRepository.save(invoice);
                }
            }
        }
    }

    private void billCourseEnrollments(DemoDataset dataset) {
        List<CourseEnrollment> enrollments = dataset.getCourseEnrollments();
        for (int i = 0; i < enrollments.size(); i++) {
            CourseEnrollment enrollment = enrollments.get(i);
            Course course = enrollment.getCourse();
            BigDecimal monthly = course.getPrice()
                    .divide(BigDecimal.valueOf(course.getDurationMonths()), 2, java.math.RoundingMode.HALF_UP);
            BillingProfile profile = BillingProfile.values()[(i + 3) % BillingProfile.values().length];
            selectPlan(dataset, null, enrollment, profile);
            int lastMonth = Math.min(2 + course.getDurationMonths(), 10);
            for (int month = 3; month <= lastMonth; month++) {
                YearMonth period = YearMonth.of(2026, month);
                Invoice invoice = buildInvoice(null, enrollment, FeeType.RECURRING_FEE, period,
                        LocalDate.of(2026, month, 10), monthly,
                        course.getName() + " — cuota " + String.format("%02d/2026", month));
                applyCurrentYearProfile(dataset, invoice, profile, false, period);
            }
            updateCourseStanding(enrollment, profile);
        }
    }

    private void selectPlan(
            DemoDataset dataset, ProgramEnrollment programEnrollment, CourseEnrollment courseEnrollment,
            BillingProfile profile) {
        PaymentPlanCadence cadence = profile == BillingProfile.UPFRONT
                ? PaymentPlanCadence.UPFRONT
                : PaymentPlanCadence.MONTHLY;
        List<PaymentPlanOption> options = programEnrollment != null
                ? paymentPlanOptionRepository.findAll().stream()
                        .filter(option -> option.getProgram() != null
                                && option.getProgram().getId().equals(programEnrollment.getProgram().getId())
                                && option.getCadence() == cadence)
                        .toList()
                : paymentPlanOptionRepository.findAll().stream()
                        .filter(option -> option.getCourse() != null
                                && option.getCourse().getId().equals(courseEnrollment.getCourse().getId())
                                && option.getCadence() == cadence)
                        .toList();
        if (options.isEmpty()) {
            return;
        }
        PaymentPlanSelection selection = new PaymentPlanSelection();
        selection.setProgramEnrollment(programEnrollment);
        selection.setCourseEnrollment(courseEnrollment);
        selection.setPaymentPlanOption(options.get(0));
        selection.setEffectiveFrom(YearMonth.of(2026, 3));
        selection.setScopeMonths(cadence == PaymentPlanCadence.UPFRONT ? 10 : null);
        selection.setCreatedBy(dataset.administrativeAt(0));
        paymentPlanSelectionRepository.save(selection);
    }

    private Scholarship createScholarship(DemoDataset dataset, ProgramEnrollment enrollment) {
        Scholarship scholarship = new Scholarship();
        scholarship.setProgramEnrollment(enrollment);
        scholarship.setValue(new MonetaryAdjustmentValue.PercentageAmount(new BigDecimal("0.50")));
        scholarship.setReason("Beca socioeconómica otorgada por dirección");
        scholarship.setGrantedBy(dataset.getDirector());
        scholarship.setGrantedAt(LocalDateTime.of(2026, 3, 1, 10, 0));
        scholarship.addInstallments(30);
        return scholarshipRepository.save(scholarship);
    }

    private Invoice buildInvoice(
            ProgramEnrollment programEnrollment, CourseEnrollment courseEnrollment, FeeType feeType,
            YearMonth period, LocalDate dueDate, BigDecimal amount, String description) {
        Invoice invoice = new Invoice();
        invoice.setProgramEnrollment(programEnrollment);
        invoice.setCourseEnrollment(courseEnrollment);
        invoice.setFeeType(feeType);
        invoice.setPeriod(period);
        invoice.setDueDate(dueDate);
        invoice.setBaseAmount(amount);
        invoice.setDescription(description);
        invoice.setStatus(InvoiceStatus.PENDING);
        invoice.setCreatedAt(dueDate.minusDays(10).atTime(8, 0));
        return invoice;
    }

    private void settle(DemoDataset dataset, Invoice invoice, PaymentMethod method, LocalDate paidOn) {
        Invoice saved = invoiceRepository.save(invoice);
        Payment payment = new Payment();
        payment.setAmount(saved.getAmountDue());
        payment.setMethod(method);
        payment.setRegisteredBy(dataset.administrativeAt(0));
        payment.setPaidAt(paidOn.atTime(12, 0));
        payment.setStatus(PaymentStatus.CONFIRMED);
        payment.setCreatedAt(paidOn.atTime(12, 0));
        if (method == PaymentMethod.MERCADO_PAGO) {
            payment.setExternalReference("MP-" + (100000000L + saved.getId()));
        }
        Payment savedPayment = paymentRepository.save(payment);
        saved.registerPayment(saved.getAmountDue(), savedPayment);
        invoiceRepository.save(saved);
        if (method == PaymentMethod.TRANSFER) {
            createReceipt(dataset, saved, PaymentReceiptStatus.APPROVED, null);
        }
    }

    private void createReceipt(DemoDataset dataset, Invoice invoice, PaymentReceiptStatus status, String rejection) {
        PaymentReceipt receipt = new PaymentReceipt();
        receipt.setInvoice(invoice);
        receipt.setFilePath("demo/comprobantes/transferencia-" + invoice.getId() + ".pdf");
        receipt.setUploadedAt(invoice.getDueDate().minusDays(2).atTime(19, 30));
        receipt.setStatus(status);
        receipt.setRejectionReason(rejection);
        if (status != PaymentReceiptStatus.PENDING_REVIEW) {
            receipt.setReviewedBy(dataset.administrativeAt(0));
            receipt.setReviewedAt(invoice.getDueDate().minusDays(1).atTime(10, 15));
        }
        paymentReceiptRepository.save(receipt);
    }

    private void updateStanding(ProgramEnrollment enrollment, BillingProfile profile) {
        enrollment.setBillingStanding(standingFor(profile));
        enrollment.setBillingStandingUpdatedAt(TODAY.atStartOfDay());
        programEnrollmentRepository.save(enrollment);
    }

    private void updateCourseStanding(CourseEnrollment enrollment, BillingProfile profile) {
        enrollment.setBillingStanding(standingFor(profile));
        enrollment.setBillingStandingUpdatedAt(TODAY.atStartOfDay());
        courseEnrollmentRepository.save(enrollment);
    }

    private EnrollmentBillingStanding standingFor(BillingProfile profile) {
        return switch (profile) {
            case ONE_OVERDUE, RECEIPT_REJECTED -> EnrollmentBillingStanding.OVERDUE;
            case SUSPENDED -> EnrollmentBillingStanding.SUSPENDED;
            default -> EnrollmentBillingStanding.UP_TO_DATE;
        };
    }

    private void createCreditNotes(DemoDataset dataset) {
        List<CreditNote> notes = new ArrayList<>();
        CreditNote first = new CreditNote();
        first.setStudent(dataset.getStudents().get(6));
        first.setAmount(new BigDecimal("96000"));
        first.setReasonType(CreditNoteReasonType.EARLY_WITHDRAWAL_UPFRONT_UNUSED);
        first.setReason("Baja en julio con pago anual anticipado: se acreditan las cuotas no utilizadas");
        first.setCreatedBy(dataset.administrativeAt(0));
        notes.add(first);

        CreditNote second = new CreditNote();
        second.setStudent(dataset.getStudents().get(14));
        second.setAmount(new BigDecimal("48000"));
        second.setReasonType(CreditNoteReasonType.ADMINISTRATIVE_CORRECTION);
        second.setReason("Cuota de agosto facturada dos veces por error administrativo");
        second.setCreatedBy(dataset.administrativeAt(1));
        notes.add(second);

        creditNoteRepository.saveAll(notes);
    }
}
