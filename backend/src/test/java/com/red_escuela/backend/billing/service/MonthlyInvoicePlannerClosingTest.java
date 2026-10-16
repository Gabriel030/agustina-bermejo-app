package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.support.BillingTestFixtures;

import com.red_escuela.backend.billing.planning.domain.EnrollmentFeeObligationStatus;
import com.red_escuela.backend.billing.planning.domain.EnrollmentFeeRequirementScope;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.dto.CreateScholarshipRequest;
import com.red_escuela.backend.billing.dto.MonetaryAdjustmentRequest;
import com.red_escuela.backend.billing.dto.SelectPaymentPlanRequest;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.entity.ScholarshipScope;
import com.red_escuela.backend.billing.usecase.paymentplan.SelectPaymentPlanForProgramUseCase;
import com.red_escuela.backend.billing.usecase.paymentreceipt.ApprovePaymentReceiptUseCase;
import com.red_escuela.backend.billing.usecase.paymentreceipt.UploadPaymentReceiptUseCase;
import com.red_escuela.backend.billing.usecase.scholarship.GrantScholarshipForProgramUseCase;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.dto.CreateSectionEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.exception.EnrollmentFeeNotPaidForTermException;
import com.red_escuela.backend.enrollment.service.SubjectSectionEnrollmentService;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.ApproveProgramEnrollmentApplicationCommand;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.ApproveProgramEnrollmentApplicationUseCase;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.RequestProgramEnrollmentApplicationCommand;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.RequestProgramEnrollmentApplicationUseCase;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.AfterEach;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.IrregularTermTypes;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.configuration.service.DirectConfigurationPublisher;
import com.red_escuela.backend.billing.planning.domain.SlotWindow;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static com.red_escuela.backend.billing.support.InvoiceAssertions.assertThatInvoice;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonthlyInvoicePlannerClosingTest extends AbstractInvoiceSequenceTest {
    @Autowired
    private SubjectSectionEnrollmentService enrollmentService;
    @Autowired
    private EnrollmentFeeRequirementService requirementService;
    @Autowired
    private UploadPaymentReceiptUseCase uploadPaymentReceiptUseCase;
    @Autowired
    private ApprovePaymentReceiptUseCase approvePaymentReceiptUseCase;
    @Autowired
    private RequestProgramEnrollmentApplicationUseCase requestApplicationUseCase;
    @Autowired
    private ApproveProgramEnrollmentApplicationUseCase approveApplicationUseCase;
    @Autowired
    private SelectPaymentPlanForProgramUseCase selectPaymentPlanUseCase;
    @Autowired
    private GrantScholarshipForProgramUseCase grantScholarshipUseCase;
    @Autowired
    private InvoicePlanningContextLoader contextLoader;
    @Autowired
    private BillingConfigurationReader configurationReader;
    @Autowired
    private DirectConfigurationPublisher configurationPublisher;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(Career career) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(career.directorId().toString(), null));
    }

    private Person director(Career career) {
        return transactionTemplate.execute(status -> entityManager.find(Person.class, career.directorId()));
    }

    private Long studentOf(Long enrollmentId) {
        return transactionTemplate.execute(status -> entityManager.find(ProgramEnrollment.class, enrollmentId).getStudent().getId());
    }

    private Invoice onlyFee(Long enrollmentId) {
        List<Invoice> fees = invoices(enrollmentId, FeeType.ENROLLMENT_FEE);
        assertThat(fees).hasSize(1);
        return fees.get(0);
    }

    private void pay(Career career, Invoice invoice, long amount) {
        BillingTestFixtures.registerApprovedBankTransferPayment(invoice.getId(), director(career).getId());
    }

    private MockMultipartFile receiptFile() {
        return new MockMultipartFile("comprobante", "comprobante.pdf", "application/pdf", "contenido".getBytes());
    }

    private EnrollmentFeeObligationStatus feeStatus(Long enrollmentId, int slot) {
        return transactionTemplate.execute(status -> requirementService.statusOf(entityManager.find(ProgramEnrollment.class, enrollmentId))
                .stream().filter(o -> o.slot() == slot).findFirst().orElseThrow().status());
    }

    private Long monthlyOption(Career career) {
        return transactionTemplate.execute(status -> {
            PaymentPlanOption option = new PaymentPlanOption();
            option.setProgram(entityManager.getReference(Program.class, career.programId()));
            option.setDescription("Mensual");
            option.setCadence(PaymentPlanCadence.MONTHLY);
            option.setCreatedBy(entityManager.getReference(Person.class, career.directorId()));
            entityManager.persist(option);
            return option.getId();
        });
    }

    @Test
    @Tag("D-001")
    void everyInstallmentIsDueTenDaysAfterItsEmissionAndTheLectiveYearHasNoInstallmentsInJanuaryNorFebruary() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        today(date(2027, 1, 4));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 1), date(2027, 1, 20));

        job.run();
        today(date(2027, 2, 2));
        job.run();
        today(date(2027, 3, 3));
        job.run();
        today(date(2027, 4, 2));
        job.run();

        assertThat(sequence(enrollment)).isEqualTo(expected("M2027-02", "C2027-03", "C2027-04"));
        assertThat(invoices(enrollment, FeeType.MONTHLY_INSTALLMENT).get(0).getDueDate()).isEqualTo(date(2027, 3, 13));
        assertThat(invoices(enrollment, FeeType.MONTHLY_INSTALLMENT).get(1).getDueDate()).isEqualTo(date(2027, 4, 12));
        Invoice reenrollmentFee = onlyFee(enrollment);
        assertThat(reenrollmentFee.getTemplateId()).isEqualTo(y27.templateId());
        assertThat(reenrollmentFee.getEnrollmentFeeSlot()).isEqualTo(1);
        assertInvariants(enrollment, y27);
    }

    @Test
    @Tag("D-057")
    void anInstallmentEmittedOnTheThirdDayIsDueOnTheThirteenth() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        today(date(2027, 3, 3));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 1), date(2027, 1, 20));

        job.run();

        assertThat(invoices(enrollment, FeeType.MONTHLY_INSTALLMENT)).extracting(Invoice::getDueDate).containsExactly(date(2027, 3, 13));
        assertInvariants(enrollment, y27);
    }

    @Test
    @Tag("D-057")
    void theYearCanConfigureTheDueDaysOfItsInstallments() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        configureYear(y27, template -> com.red_escuela.backend.billing.planning.TestTemplateTerms.setRecurringFeeDueDays(template, 5));
        today(date(2027, 3, 2));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 1), date(2027, 1, 20));

        job.run();

        assertThat(invoices(enrollment, FeeType.MONTHLY_INSTALLMENT)).extracting(Invoice::getDueDate).containsExactly(date(2027, 3, 7));
    }

    @Test
    void runningAPastMonthChargesTheEmissionDayPriceAndDuesTenDaysFromTheRealEmissionDate() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        today(date(2027, 1, 4));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 1), date(2027, 1, 20));
        newPrice(career, FeeType.MONTHLY_INSTALLMENT, 12000, date(2027, 5, 1));
        today(date(2027, 6, 3));

        job.run(YearMonth.of(2027, 4));

        List<Invoice> installments = invoices(enrollment, FeeType.MONTHLY_INSTALLMENT);
        assertThat(installments).extracting(Invoice::getPeriod).containsExactly(YearMonth.of(2027, 4));
        assertThat(installments.get(0).getDueDate()).isEqualTo(date(2027, 6, 13));
        assertThat(installments.get(0).getDueDate()).isAfterOrEqualTo(date(2027, 6, 3));
        assertThat(amountDue(installments.get(0))).isEqualTo(ars(12000));
        assertInvariants(enrollment, y27);
    }

    @Test
    void aReceiptUnderReviewDoesNotEnableTheEnrollmentButApprovingItDoes() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        Term first = newCuatrimestre(2027, 3, date(2027, 2, 1), date(2027, 3, 10), "1C");
        Long section = newSection(career, first);
        today(date(2027, 2, 5));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 1), date(2027, 1, 20));
        authenticateAs(career);

        assertThatThrownBy(() -> enrollmentService.enroll(enrollment, new CreateSectionEnrollmentRequest(section)))
                .isInstanceOf(EnrollmentFeeNotPaidForTermException.class);
        Invoice fee = onlyFee(enrollment);
        assertThat(feeStatus(enrollment, 1)).isEqualTo(EnrollmentFeeObligationStatus.PENDING);

        BankTransferReceipt receipt = uploadPaymentReceiptUseCase.upload(fee.getId(), studentOf(enrollment), receiptFile());
        assertThat(feeStatus(enrollment, 1)).isEqualTo(EnrollmentFeeObligationStatus.IN_REVIEW);
        assertThatThrownBy(() -> enrollmentService.enroll(enrollment, new CreateSectionEnrollmentRequest(section)))
                .isInstanceOf(EnrollmentFeeNotPaidForTermException.class);

        approvePaymentReceiptUseCase.approve(receipt.getId(), director(career));
        assertThat(feeStatus(enrollment, 1)).isEqualTo(EnrollmentFeeObligationStatus.PAID);
        assertThatCode(() -> enrollmentService.enroll(enrollment, new CreateSectionEnrollmentRequest(section))).doesNotThrowAnyException();
        assertInvariants(enrollment, y27);
    }

    @Test
    @Tag("D-019")
    void theAdmissionFeeFlowLeavesASingleEntryFeeThatIsNeverDiscountedOnceItWasPaid() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        Long cohort = newCohort(y27, CohortIntake.SECOND, date(2027, 3, 1));
        today(date(2027, 2, 10));
        Person preceptor = director(career);
        Student student = transactionTemplate.execute(status -> {
            Student created = new Student();
            created.setPerson(newPerson());
            created.setInstitution(entityManager.getReference(Institution.class, career.institutionId()));
            created.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(created);
            return created;
        });

        ProgramEnrollmentApplication application = requestApplicationUseCase.request(
                new RequestProgramEnrollmentApplicationCommand(student, career.programId(), cohort));
        ProgramEnrollmentApplication approved = approveApplicationUseCase.approve(new ApproveProgramEnrollmentApplicationCommand(application.getId(), preceptor));
        Long admissionInvoiceId = approved.getAdmissionInvoice().getId();
        BillingTestFixtures.registerApprovedBankTransferPayment(admissionInvoiceId, preceptor.getId());
        Long enrollment = org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(30)).until(() -> transactionTemplate.execute(status ->
                entityManager.createQuery("select e.id from ProgramEnrollment e where e.student.id = :id", Long.class)
                        .setParameter("id", student.getId()).getResultList().stream().findFirst().orElse(null)), java.util.Objects::nonNull);
        authenticateAs(career);
        selectPaymentPlanUseCase.select(enrollment, new SelectPaymentPlanRequest(monthlyOption(career), null));
        job.run();
        today(date(2027, 3, 3));
        job.run();

        Invoice entryFee = onlyFee(enrollment);
        assertThat(entryFee.getTemplateId()).isEqualTo(y27.templateId());
        assertThat(entryFee.getEnrollmentFeeSlot()).isEqualTo(1);
        assertThatInvoice(entryFee).isPaid();
        assertThat(amountDue(entryFee)).isEqualTo(ars(5000));
        assertThat(invoices(enrollment, FeeType.MONTHLY_INSTALLMENT)).extracting(Invoice::getPeriod).containsExactly(YearMonth.of(2027, 3));
        assertInvariants(enrollment, y27);
    }

    @Test
    @Tag("D-014")
    void examRegistrationsRequireTheFeeOfTheTermTheStudentTookAlsoInFebruaryOfTheNextYear() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 2, 2, 7);
        Year y28 = newYear(career, 2028, 10, 1, 2, null);
        Term first = newCuatrimestre(2027, 3, date(2027, 2, 1), date(2027, 3, 10), "1C");
        Term second = newCuatrimestre(2027, 8, date(2027, 7, 1), date(2027, 8, 10), "2C");
        newSection(career, first);
        newSection(career, second);
        today(date(2027, 2, 5));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 1), date(2027, 1, 20));
        job.run();
        today(date(2027, 7, 3));
        job.run();
        Invoice firstFee = invoices(enrollment, FeeType.ENROLLMENT_FEE).stream().filter(i -> i.getEnrollmentFeeSlot() == 1).findFirst().orElseThrow();
        Invoice secondFee = invoices(enrollment, FeeType.ENROLLMENT_FEE).stream().filter(i -> i.getEnrollmentFeeSlot() == 2).findFirst().orElseThrow();
        authenticateAs(career);
        pay(career, firstFee, 5000);
        today(date(2028, 2, 3));
        job.run();
        assertThatCode(() -> requireForTerm(enrollment, first)).doesNotThrowAnyException();
        assertThatThrownBy(() -> requireForTerm(enrollment, second)).isInstanceOf(EnrollmentFeeNotPaidForTermException.class);
        Invoice olderInstallment = invoices(enrollment, FeeType.MONTHLY_INSTALLMENT).get(0);
        pay(career, olderInstallment, olderInstallment.getAmounts().outstandingAmount().value().amount().longValueExact());
        pay(career, secondFee, 5000);
        assertThatCode(() -> requireForTerm(enrollment, second)).doesNotThrowAnyException();
        List<Invoice> next = invoices(enrollment, FeeType.ENROLLMENT_FEE).stream().filter(i -> y28.templateId().equals(i.getTemplateId())).toList();
        assertThat(next).hasSize(1);
        assertThatInvoice(next.get(0)).isNotPaid();
        assertInvariants(enrollment, y27, y28);
    }

    private void requireForTerm(Long enrollmentId, Term term) {
        transactionTemplate.executeWithoutResult(status -> requirementService.requireForTerm(
                entityManager.find(ProgramEnrollment.class, enrollmentId), entityManager.find(Term.class, term.getId()),
                EnrollmentFeeRequirementScope.EXAM_REGISTRATION));
    }

    @Test
    @Tag("D-157")
    void aScholarshipNeverDiscountsTheEnrollmentFeeAndOnlyDiscountsTheInstallments() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        today(date(2027, 1, 4));
        Long cohort = newCohort(y27, CohortIntake.FIRST, date(2027, 3, 1));
        Long forFee = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 1), date(2027, 1, 20));
        Long forInstallments = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 1), date(2027, 1, 20));
        authenticateAs(career);
        grantScholarshipUseCase.grant(forFee, scholarship(ScholarshipScope.ENROLLMENT_FEE));
        grantScholarshipUseCase.grant(forInstallments, scholarship(null));

        today(date(2027, 2, 2));
        job.run();
        today(date(2027, 3, 2));
        job.run();

        assertThat(amountDue(onlyFee(forFee))).isEqualTo(ars(5000));
        assertThat(amountDue(invoices(forFee, FeeType.MONTHLY_INSTALLMENT).get(0))).isEqualTo(ars(10000));
        assertThat(amountDue(onlyFee(forInstallments))).isEqualTo(ars(5000));
        assertThat(amountDue(invoices(forInstallments, FeeType.MONTHLY_INSTALLMENT).get(0))).isEqualTo(ars(5000));
        assertInvariants(forFee, y27);
        assertInvariants(forInstallments, y27);
    }

    private CreateScholarshipRequest scholarship(ScholarshipScope scope) {
        return new CreateScholarshipRequest(new MonetaryAdjustmentRequest(MonetaryAdjustmentRequest.Type.PROPORTIONAL, null,
                Percentage.ofFraction(new BigDecimal("0.5"))), "Beca 50%", 3, scope);
    }

    @Test
    @Tag("D-142")
    void aSuspendedStudentWhoDoesNotPayIsWithdrawnWhenTheTermOfTheSuspensionEndsWithTheDebtStillOwed() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        Term first = newCuatrimestre(2027, 3, date(2027, 2, 1), date(2027, 3, 10), "1C");
        Term second = newCuatrimestre(2027, 8, date(2027, 7, 1), date(2027, 8, 10), "2C");
        newSection(career, first);
        newSection(career, second);
        today(date(2027, 1, 4));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 1), date(2027, 1, 20));
        runEveryMonth(YearMonth.of(2027, 1), YearMonth.of(2027, 4), () -> {
        });
        suspend(enrollment, LocalDateTime.of(2027, 4, 20, 10, 0));
        List<String> frozen = sequence(enrollment);

        today(date(2027, 7, 20));
        job.run();
        assertThat(entityManager.find(ProgramEnrollment.class, enrollment).getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);

        today(date(2027, 8, 4));
        job.run();

        ProgramEnrollment closed = entityManager.find(ProgramEnrollment.class, enrollment);
        assertThat(closed.getStatus()).isEqualTo(ProgramEnrollmentStatus.WITHDRAWN);
        assertThat(closed.getPaymentStatus()).isEqualTo(EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT);
        assertThat(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment)).isNotEmpty()
                .noneMatch(i -> i.getPaymentStatus() == InvoicePaymentStatus.CANCELLED)
                .anyMatch(i -> i.getPaymentStatus() == InvoicePaymentStatus.PENDING || i.getPaymentStatus() == InvoicePaymentStatus.OVERDUE);
        assertThat(sequence(enrollment)).isEqualTo(frozen);
        assertInvariants(enrollment, y27);
    }

    @Test
    void theSuspendedWithdrawalCanBeConfiguredToWaitForTheEndOfTheLectiveYear() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        configureYear(y27, template -> com.red_escuela.backend.billing.planning.TestTemplateTerms.setWithdrawSuspendedAtTermEnd(template, false));
        Term first = newCuatrimestre(2027, 3, date(2027, 2, 1), date(2027, 3, 10), "1C");
        newSection(career, first);
        today(date(2027, 1, 4));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 1), date(2027, 1, 20));
        runEveryMonth(YearMonth.of(2027, 1), YearMonth.of(2027, 4), () -> {
        });
        suspend(enrollment, LocalDateTime.of(2027, 4, 20, 10, 0));

        today(date(2027, 8, 4));
        job.run();
        assertThat(entityManager.find(ProgramEnrollment.class, enrollment).getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);

        today(date(2028, 1, 4));
        job.run();
        assertThat(entityManager.find(ProgramEnrollment.class, enrollment).getStatus()).isEqualTo(ProgramEnrollmentStatus.WITHDRAWN);
    }

    private void suspend(Long enrollment, LocalDateTime on) {
        setStanding(enrollment, EnrollmentPaymentStatus.SUSPENDED);
        transactionTemplate.executeWithoutResult(status -> entityManager.find(ProgramEnrollment.class, enrollment).setPaymentStatusUpdatedAt(on));
    }

    @Test
    @Tag("D-008")
    void noTermTypeIsIrregularByDefault() {
        assertThat(configurationReader.irregularTermTypesEffectiveOn(date(2027, 1, 1)).types()).isEmpty();
    }

    @Test
    @Tag("D-213")
    void theGlobalIrregularListRemovesThoseTermsFromTheEnrollmentWindows() {
        Career career = newCareer();
        newYear(career, 2027, 10, 1, 2, null);
        Term regular = newCuatrimestre(2027, 3, date(2027, 2, 1), date(2027, 3, 10), "1C");
        Term summer = newCuatrimestre(2027, 3, date(2027, 1, 5), date(2027, 3, 20), "Intensivo de verano");
        newSection(career, regular);
        newSection(career, summer);
        today(date(2027, 1, 10));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 1), date(2027, 1, 8));

        assertThat(windowOpeningDates(enrollment)).containsExactly(date(2027, 1, 5));

        publishIrregularTypes(career, List.of("Intensivo de verano"));
        try {
            assertThat(windowOpeningDates(enrollment)).containsExactly(date(2027, 2, 1));
        } finally {
            publishIrregularTypes(career, List.of());
        }
        assertThat(windowOpeningDates(enrollment)).containsExactly(date(2027, 1, 5));
    }

    private List<LocalDate> windowOpeningDates(Long enrollment) {
        return transactionTemplate.execute(status -> contextLoader.load(entityManager.find(ProgramEnrollment.class, enrollment), YearMonth.of(2027, 1), date(2027, 1, 10))
                .facts().windows().values().stream().map(SlotWindow::opensOn).toList());
    }

    private void publishIrregularTypes(Career career, List<String> types) {
        transactionTemplate.executeWithoutResult(status -> configurationPublisher.publishVersion(ConfigurationTopic.ofGlobal(ConfigurationScope.IRREGULAR_TERMS),
                new IrregularTermTypes(types), date(2027, 1, 10), "Prueba de D-213", career.directorId()));
    }
}
