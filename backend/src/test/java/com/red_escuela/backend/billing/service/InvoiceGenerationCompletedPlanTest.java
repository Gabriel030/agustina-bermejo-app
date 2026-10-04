package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.shared.domain.Grade;

import com.red_escuela.backend.academicstructure.entity.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.dto.RegisterPaymentRequest;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.PaymentMethod;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.exception.PendingDebtException;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.usecase.payment.RegisterPaymentUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.usecase.graduationrequest.RequestGraduationUseCase;
import com.red_escuela.backend.graduation.usecase.graduationrequest.VerifyGraduationRequestDebtUseCase;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class InvoiceGenerationCompletedPlanTest extends AbstractIntegrationTest {

    private static final YearMonth FIRST_MONTH = YearMonth.of(2026, 3);

    @Autowired
    private InvoiceGenerationService invoiceGenerationService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private RequestGraduationUseCase requestGraduationUseCase;

    @Autowired
    private VerifyGraduationRequestDebtUseCase verifyGraduationRequestDebtUseCase;

    @Autowired
    private RegisterPaymentUseCase registerPaymentUseCase;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Person persistPerson(String dni) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Persona");
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        return person;
    }

    private Student persistStudent(String dni) {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        Student student = new Student();
        student.setPerson(persistPerson(dni));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private Term persistTerm() {
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026");
        term.setStatus(StatusType.ACTIVE);
        term.setStartDate(LocalDateTime.of(2026, 3, 1, 0, 0));
        term.setEndDate(LocalDateTime.of(2026, 12, 31, 23, 59));
        entityManager.persist(term);
        return term;
    }

    private Subject persistMandatorySubject(Program program) {
        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Materia del plan " + System.nanoTime());
        entityManager.persist(subject);
        ProgramSubject programSubject = new ProgramSubject();
        programSubject.setProgram(program);
        programSubject.setSubject(subject);
        programSubject.setCredits(10);
        programSubject.setCurricularYear(1);
        programSubject.setCurricularTerm(CurricularTerm.FIRST);
        programSubject.setStatus(StatusType.ACTIVE);
        entityManager.persist(programSubject);
        return subject;
    }

    private ProgramEnrollment persistMonthlyBilledEnrollment(Student student, Program program, Term term, Person director) {
        ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
        template.setProgram(program);
        template.setTerm(term);
        template.setRecurringFeeInvoicesCount(10);
        template.setCreatedBy(director);
        entityManager.persist(template);

        ProgramCohort cohort = new ProgramCohort();
        cohort.setTemplate(template);
        cohort.setIntake(CohortIntake.FIRST);
        cohort.setAdmissionStart(LocalDate.of(2026, 1, 1));
        cohort.setAdmissionEnd(LocalDate.of(2026, 2, 28));
        cohort.setCursadaStart(LocalDate.of(2026, 3, 1));
        cohort.setCreatedBy(director);
        entityManager.persist(cohort);

        PriceRevision price = new PriceRevision();
        price.setProgram(program);
        price.setFeeType(FeeType.RECURRING_FEE);
        price.setDescription("Cuota mensual");
        price.setMonthlyAmount(BigDecimal.valueOf(10000));
        price.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        price.setCreatedBy(director);
        entityManager.persist(price);

        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setProgramCohort(cohort);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.of(2026, 2, 1, 10, 0));
        entityManager.persist(enrollment);

        PaymentPlanOption option = new PaymentPlanOption();
        option.setProgram(program);
        option.setDescription("Mensual");
        option.setCadence(PaymentPlanCadence.MONTHLY);
        option.setCreatedBy(director);
        entityManager.persist(option);

        PaymentPlanSelection selection = new PaymentPlanSelection();
        selection.setProgramEnrollment(enrollment);
        selection.setPaymentPlanOption(option);
        selection.setEffectiveFrom(FIRST_MONTH);
        selection.setCreatedBy(director);
        entityManager.persist(selection);
        entityManager.flush();
        return enrollment;
    }

    private void approveSubject(Student student, ProgramEnrollment enrollment, Subject subject, Term term) {
        SubjectSection section = new SubjectSection();
        section.setName("Com A");
        section.setSubject(subject);
        section.setTerm(term);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        SubjectSectionEnrollment sectionEnrollment = new SubjectSectionEnrollment();
        sectionEnrollment.setStudent(student);
        sectionEnrollment.setProgramEnrollment(enrollment);
        sectionEnrollment.setSubjectSection(section);
        sectionEnrollment.setStatus(EnrollmentStatus.APPROVED);
        sectionEnrollment.setFinalScore(Grade.of(BigDecimal.valueOf(8.0)));
        sectionEnrollment.setEnrolledAt(LocalDateTime.of(2026, 3, 1, 10, 0));
        sectionEnrollment.setAttemptNumber(1);
        entityManager.persist(sectionEnrollment);
    }

    private Program persistProgram() {
        Program program = new Program();
        program.setName("Carrera facturada " + System.nanoTime());
        program.setTotalYears(1);
        program.setTotalCreditsRequired(10);
        entityManager.persist(program);
        return program;
    }

    private record BilledStudent(Person director, Student student, Subject subject, Term term, ProgramEnrollment enrollment) {
    }

    private BilledStudent setUpMonthlyBilledStudent(String directorDni, String studentDni) {
        return transactionTemplate.execute(status -> {
            Person director = persistPerson(directorDni);
            Student student = persistStudent(studentDni);
            Program program = persistProgram();
            Subject subject = persistMandatorySubject(program);
            Term term = persistTerm();
            ProgramEnrollment enrollment = persistMonthlyBilledEnrollment(student, program, term, director);
            return new BilledStudent(director, student, subject, term, enrollment);
        });
    }

    private void approveTheWholePlan(BilledStudent billed) {
        transactionTemplate.executeWithoutResult(status ->
                approveSubject(entityManager.getReference(Student.class, billed.student().getId()),
                        entityManager.getReference(ProgramEnrollment.class, billed.enrollment().getId()),
                        entityManager.getReference(Subject.class, billed.subject().getId()),
                        entityManager.getReference(Term.class, billed.term().getId())));
    }

    private List<Invoice> recurringInvoices(ProgramEnrollment enrollment) {
        return invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId()).stream()
                .filter(invoice -> invoice.getFeeType() == FeeType.RECURRING_FEE)
                .toList();
    }

    @Test
    void studentWhoCompletedThePlanDoesNotReceiveTheNextMonthlyInstallment() {
        BilledStudent billed = setUpMonthlyBilledStudent("93000001", "93000002");
        invoiceGenerationService.generateNextInstallmentForProgram(billed.enrollment(), FIRST_MONTH);
        approveTheWholePlan(billed);

        invoiceGenerationService.generateNextInstallmentForProgram(billed.enrollment(), FIRST_MONTH.plusMonths(1));

        assertThat(recurringInvoices(billed.enrollment())).extracting(Invoice::getPeriod).containsExactly(FIRST_MONTH);
    }

    @Test
    void studentWithAPendingMandatorySubjectKeepsReceivingMonthlyInstallments() {
        BilledStudent billed = setUpMonthlyBilledStudent("93000003", "93000004");

        invoiceGenerationService.generateNextInstallmentForProgram(billed.enrollment(), FIRST_MONTH);
        invoiceGenerationService.generateNextInstallmentForProgram(billed.enrollment(), FIRST_MONTH.plusMonths(1));

        assertThat(recurringInvoices(billed.enrollment())).extracting(Invoice::getPeriod)
                .containsExactly(FIRST_MONTH, FIRST_MONTH.plusMonths(1));
    }

    @Test
    void programWithAnEmptyStudyPlanKeepsReceivingMonthlyInstallments() {
        ProgramEnrollment enrollment = transactionTemplate.execute(status -> {
            Person director = persistPerson("93000007");
            Student student = persistStudent("93000008");
            Program program = new Program();
            program.setName("Carrera sin plan cargado " + System.nanoTime());
            program.setTotalYears(1);
            program.setTotalCreditsRequired(0);
            entityManager.persist(program);
            return persistMonthlyBilledEnrollment(student, program, persistTerm(), director);
        });

        invoiceGenerationService.generateNextInstallmentForProgram(enrollment, FIRST_MONTH);

        assertThat(recurringInvoices(enrollment)).extracting(Invoice::getPeriod).containsExactly(FIRST_MONTH);
    }

    @Test
    void completedPlanStopsBillingButTheUnpaidInstallmentBlocksGraduationUntilItIsPaid() {
        BilledStudent billed = setUpMonthlyBilledStudent("93000005", "93000006");
        invoiceGenerationService.generateNextInstallmentForProgram(billed.enrollment(), FIRST_MONTH);
        approveTheWholePlan(billed);

        invoiceGenerationService.generateNextInstallmentForProgram(billed.enrollment(), FIRST_MONTH.plusMonths(1));
        List<Invoice> invoices = recurringInvoices(billed.enrollment());
        assertThat(invoices).hasSize(1);
        Invoice unpaid = invoices.get(0);
        assertThat(unpaid.getStatus()).isEqualTo(InvoiceStatus.PENDING);
        BigDecimal balance = transactionTemplate.execute(status ->
                invoiceRepository.findById(unpaid.getId()).orElseThrow().getRemainingBalance());

        GraduationRequest request = requestGraduationUseCase.request(billed.enrollment().getId());
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(billed.director().getId().toString(), null));
        assertThatThrownBy(() -> verifyGraduationRequestDebtUseCase.verify(request.getId()))
                .isInstanceOf(PendingDebtException.class);

        registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(unpaid.getId(), balance)),
                PaymentMethod.CASH, false, null, null, null, null), billed.director());

        assertThat(verifyGraduationRequestDebtUseCase.verify(request.getId()).getStatus())
                .isEqualTo(GraduationRequestStatus.ADMIN_VERIFIED);
    }
}
