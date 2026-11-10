package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.support.StudentDocumentFixtures;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.GetProgramEnrollmentApplicationApprovalCheckUseCase;
import com.red_escuela.backend.testsupport.TermFixtures;
import com.red_escuela.backend.billing.operation.PaymentVerification;

import com.red_escuela.backend.billing.operation.ReportedGatewayAllocation;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationResponse;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.exception.CohortAdmissionWindowClosedException;
import com.red_escuela.backend.enrollment.exception.AlreadyHasPendingApplicationException;
import com.red_escuela.backend.enrollment.exception.InvalidProgramEnrollmentApplicationStateException;
import com.red_escuela.backend.enrollment.exception.DniRequiredException;
import com.red_escuela.backend.billing.pricing.exception.NoEffectivePriceException;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.enrollment.exception.ApplicationDocumentsIncompleteException;
import java.time.LocalDate;
import java.util.List;

import static com.red_escuela.backend.billing.support.InvoiceAssertions.assertThatInvoice;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@org.springframework.test.context.event.RecordApplicationEvents
class ProgramEnrollmentApplicationUseCaseTest extends AbstractIntegrationTest {
    @Autowired
    private com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader configurationReader;

    @Autowired
    private RequestProgramEnrollmentApplicationUseCase requestProgramEnrollmentApplicationUseCase;

    @Autowired
    private ApproveProgramEnrollmentApplicationUseCase approveProgramEnrollmentApplicationUseCase;

    @Autowired
    private RejectProgramEnrollmentApplicationUseCase rejectProgramEnrollmentApplicationUseCase;

    @Autowired
    private com.red_escuela.backend.billing.operation.ApprovedPaymentRegistrationPort registrationPort;

    @Autowired
    private ProgramEnrollmentRepository programEnrollmentRepository;

    @Autowired
    private com.red_escuela.backend.enrollment.service.ProgramEnrollmentService programEnrollmentService;

    @Autowired
    private com.red_escuela.backend.billing.payment.repository.PaymentRepository paymentRepository;

    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentreceipt.UploadPaymentReceiptUseCase uploadPaymentReceiptUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentreceipt.ApprovePaymentReceiptUseCase approvePaymentReceiptUseCase;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    private Institution persistInstitution() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        return institution;
    }

    private Person persistPersonBase(String dni) {
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
        Student student = new Student();
        student.setPerson(persistPersonBase(dni));
        student.setInstitution(persistInstitution());
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private Program persistProgram(String nombre) {
        Program program = new Program();
        program.setVisibleInCatalog(true);
        program.setName(nombre);
        program.setTotalYears(3);
        program.setTotalCreditsRequired(0);
        entityManager.persist(program);
        return program;
    }

    private ProgramCohort persistCohort(Program program, LocalDate admissionStart, LocalDate admissionEnd, Person createdBy) {
        Term term = TermFixtures.newTerm();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);

        ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
        template.setProgram(program);
        template.setTerm(term);
        com.red_escuela.backend.billing.planning.TestTemplateTerms.setRecurringFeeInvoicesCount(template, 10);
        template.setCreatedBy(createdBy);
        entityManager.persist(template);
        com.red_escuela.backend.billing.planning.TestTemplateTerms.publish(entityManager, template);

        ProgramCohort cohort = new ProgramCohort();
        cohort.setTemplate(template);
        cohort.setIntake(CohortIntake.FIRST);
        cohort.setAdmissionStart(admissionStart == null ? LocalDate.now().minusDays(30) : admissionStart);
        cohort.setAdmissionEnd(admissionEnd == null ? LocalDate.now().plusDays(30) : admissionEnd);
        cohort.setCursadaStart(LocalDate.now());
        cohort.setCreatedBy(createdBy);
        entityManager.persist(cohort);
        return cohort;
    }

    private void persistPriceRevision(Program program, BigDecimal amount, Person createdBy) {
        com.red_escuela.backend.billing.support.PriceFixtures.persistPriceVersion(program.getId(), null, FeeType.ENROLLMENT_FEE, "Matrícula de prueba", ars(amount), LocalDate.now().minusDays(1), createdBy.getId());
    }

    private ProgramEnrollmentApplication requestApplication(String dni) {
        Student student = persistStudent(dni);
        Program program = persistProgram("Diseño Gráfico " + System.nanoTime());
        Person director = persistPersonBase("DIR" + System.nanoTime());
        ProgramCohort cohort = persistCohort(program, null, null, director);
        persistPriceRevision(program, BigDecimal.valueOf(50000), director);
        StudentDocumentFixtures.persistAllRequiredDocuments(entityManager, student);
        entityManager.flush();
        return requestProgramEnrollmentApplicationUseCase.request(new RequestProgramEnrollmentApplicationCommand(student, program.getId(), cohort.getId()));
    }

    private ProgramEnrollmentApplication requestApplicationWithoutDocuments(String dni, DocumentType omitted) {
        ProgramEnrollmentApplication application = requestApplication(dni);
        Long studentId = application.getStudent().getId();
        entityManager.flush();
        entityManager.createQuery("delete from StudentDocument d where d.student.id = :id and d.type = :type")
                .setParameter("id", studentId).setParameter("type", omitted).executeUpdate();
        entityManager.clear();
        return application;
    }

    @Test
    @Tag("D-227")
    void approvingWithoutOneRequiredDocumentIsRejectedAndKeepsTheApplicationPending() {
        ProgramEnrollmentApplication application = requestApplicationWithoutDocuments("81000040", DocumentType.DNI_DORSO);
        Person preceptor = persistPreceptor();

        assertThatThrownBy(() -> approveProgramEnrollmentApplicationUseCase.approve(new ApproveProgramEnrollmentApplicationCommand(application.getId(), preceptor)))
                .isInstanceOf(ApplicationDocumentsIncompleteException.class)
                .hasMessageContaining("DNI_DORSO");

        assertThat(admissionInvoicesOf(entityManager.find(ProgramEnrollmentApplication.class, application.getId()).getStudent())).isZero();
        assertThat(entityManager.find(ProgramEnrollmentApplication.class, application.getId()).getStatus()).isEqualTo(ProgramEnrollmentApplicationStatus.PENDING);
    }

    @Test
    @Tag("D-227")
    void approvingWithoutAnyDocumentIsRejected() {
        ProgramEnrollmentApplication application = requestApplication("81000041");
        entityManager.flush();
        entityManager.createQuery("delete from StudentDocument d where d.student.id = :id").setParameter("id", application.getStudent().getId()).executeUpdate();
        entityManager.clear();
        Person preceptor = persistPreceptor();

        assertThatThrownBy(() -> approveProgramEnrollmentApplicationUseCase.approve(new ApproveProgramEnrollmentApplicationCommand(application.getId(), preceptor)))
                .isInstanceOf(ApplicationDocumentsIncompleteException.class);
    }

    private Person persistPreceptor() {
        Person preceptor = persistPersonBase("PREC" + System.nanoTime());
        preceptor.getRoles().add(roleRepository.findByName("PRECEPTOR").orElseThrow());
        entityManager.flush();
        return preceptor;
    }

    private long admissionInvoicesOf(Student student) {
        entityManager.flush();
        return ((Number) entityManager.createNativeQuery("""
                select count(*) from invoice i join student_current_account a on a.id = i.student_current_account_id
                where a.student_id = :studentId and i.issuing_reason = 'PROGRAM_ADMISSION'
                """).setParameter("studentId", student.getId()).getSingleResult()).longValue();
    }

    private boolean isEnrolled(Student student, Program program) {
        entityManager.flush();
        return !programEnrollmentRepository.findByStudentIdAndProgramIdAndStatusIn(
                student.getId(), program.getId(), List.of(ProgramEnrollmentStatus.ENROLLED)).isEmpty();
    }

    @Test
    @Tag("D-227")
    void requestingCreatesAPendingApplicationWithoutAdmissionInvoice() {
        ProgramEnrollmentApplication application = requestApplication("81000001");

        assertThat(application.getStatus()).isEqualTo(ProgramEnrollmentApplicationStatus.PENDING);
        assertThat(application.getAdmissionInvoice()).isNull();
        assertThat(admissionInvoicesOf(application.getStudent())).isZero();
    }

    @Test
    @Tag("D-227")
    void requestingTwiceWhileTheFirstIsStillPendingFails() {
        ProgramEnrollmentApplication first = requestApplication("81000002");

        assertThatThrownBy(() -> requestProgramEnrollmentApplicationUseCase.request(new RequestProgramEnrollmentApplicationCommand(
                first.getStudent(), first.getProgram().getId(), first.getProgramCohort().getId())))
                .isInstanceOf(AlreadyHasPendingApplicationException.class);
    }

    @Test
    @Tag("D-227")
    void requestingOutsideTheAdmissionWindowFailsWithoutCreatingAnything() {
        Student student = persistStudent("81000005");
        Program program = persistProgram("Diseño Gráfico " + System.nanoTime());
        Person director = persistPersonBase("DIR" + System.nanoTime());
        ProgramCohort cohort = persistCohort(program, LocalDate.now().minusDays(30), LocalDate.now().minusDays(7), director);
        persistPriceRevision(program, BigDecimal.valueOf(50000), director);
        entityManager.flush();
        long invoicesBefore = invoiceRepository.count();

        assertThatThrownBy(() -> requestProgramEnrollmentApplicationUseCase.request(new RequestProgramEnrollmentApplicationCommand(student, program.getId(), cohort.getId())))
                .isInstanceOf(CohortAdmissionWindowClosedException.class);

        assertThat(invoiceRepository.count()).isEqualTo(invoicesBefore);
    }

    @Test
    @Tag("D-228")
    void requestingWithoutDniIsRejectedAndNothingIsCreated() {
        Student student = persistStudent("81000008");
        student.getPerson().setDni(null);
        Program program = persistProgram("Diseño Gráfico " + System.nanoTime());
        Person director = persistPersonBase("DIR" + System.nanoTime());
        ProgramCohort cohort = persistCohort(program, null, null, director);
        persistPriceRevision(program, BigDecimal.valueOf(50000), director);
        entityManager.flush();

        assertThatThrownBy(() -> requestProgramEnrollmentApplicationUseCase.request(new RequestProgramEnrollmentApplicationCommand(student, program.getId(), cohort.getId())))
                .isInstanceOf(DniRequiredException.class);

        assertThat(entityManager.createQuery("select count(a) from ProgramEnrollmentApplication a where a.student.id = :id", Long.class)
                .setParameter("id", student.getId()).getSingleResult()).isZero();
    }

    @Test
    @Tag("D-227")
    void approvingIssuesTheAdmissionInvoiceOnceWithTheConfiguredDueDaysAndDoesNotEnrollYet() {
        ProgramEnrollmentApplication application = requestApplication("81000004");

        approveProgramEnrollmentApplicationUseCase.approve(new ApproveProgramEnrollmentApplicationCommand(application.getId(), persistPreceptor()));

        entityManager.flush();
        entityManager.clear();
        ProgramEnrollmentApplication approved = entityManager.find(ProgramEnrollmentApplication.class, application.getId());
        assertThat(approved.getStatus()).isEqualTo(ProgramEnrollmentApplicationStatus.APPROVED);
        Invoice invoice = approved.getAdmissionInvoice();
        assertThatInvoice(invoice).isPending();
        assertThat(invoice.getAmounts().linesTotal().value()).isEqualTo(ars(50000));
        assertThat(invoice.getDueDate()).isEqualTo(LocalDate.now().plusDays(configurationReader.requireYearTermsEffectiveOn(approved.getProgramCohort().getTemplate().programId(), approved.getProgramCohort().getTemplate().termId(), LocalDate.of(2100, 1, 1)).lateEnrollmentFeeDueDays()));
        assertThat(admissionInvoicesOf(approved.getStudent())).isEqualTo(1L);
        assertThat(isEnrolled(approved.getStudent(), approved.getProgram())).isFalse();
        assertThat(approved.getStudent().getPerson().getRoles()).extracting(Role::getName).contains("ALUMNO");

        assertThatThrownBy(() -> approveProgramEnrollmentApplicationUseCase.approve(new ApproveProgramEnrollmentApplicationCommand(application.getId(), persistPreceptor())))
                .isInstanceOf(InvalidProgramEnrollmentApplicationStateException.class);
        assertThat(admissionInvoicesOf(approved.getStudent())).isEqualTo(1L);
    }

    @Test
    @Tag("D-227")
    void rejectingTheApplicationLeavesNoInvoiceNorDebt() {
        ProgramEnrollmentApplication application = requestApplication("81000006");

        rejectProgramEnrollmentApplicationUseCase.reject(
                new RejectProgramEnrollmentApplicationCommand(application.getId(), persistPreceptor(), "Documentación ilegible"));

        entityManager.flush();
        entityManager.clear();
        ProgramEnrollmentApplication rejected = entityManager.find(ProgramEnrollmentApplication.class, application.getId());
        assertThat(rejected.getStatus()).isEqualTo(ProgramEnrollmentApplicationStatus.REJECTED);
        assertThat(rejected.getAdmissionInvoice()).isNull();
        assertThat(admissionInvoicesOf(rejected.getStudent())).isZero();
    }

    @Test
    @Tag("D-227")
    void approvingWithoutAConfiguredPriceRevisionFailsAndKeepsTheApplicationPending() {
        Student student = persistStudent("81000003");
        Program program = persistProgram("Sin Precio " + System.nanoTime());
        Person director = persistPersonBase("DIR" + System.nanoTime());
        ProgramCohort cohort = persistCohort(program, null, null, director);
        StudentDocumentFixtures.persistAllRequiredDocuments(entityManager, student);
        entityManager.flush();
        ProgramEnrollmentApplication application = requestProgramEnrollmentApplicationUseCase.request(
                new RequestProgramEnrollmentApplicationCommand(student, program.getId(), cohort.getId()));

        assertThatThrownBy(() -> approveProgramEnrollmentApplicationUseCase.approve(new ApproveProgramEnrollmentApplicationCommand(application.getId(), persistPreceptor())))
                .isInstanceOf(NoEffectivePriceException.class);
    }

    @Test
    @Tag("D-227")
    void aMercadoPagoPaymentPaysTheAdmissionInvoiceWhichHasNoEnrollmentYet() {
        ProgramEnrollmentApplication application = requestApplication("81900001");
        approveProgramEnrollmentApplicationUseCase.approve(new ApproveProgramEnrollmentApplicationCommand(application.getId(), persistPreceptor()));
        Invoice invoice = application.getAdmissionInvoice();
        entityManager.flush();
        String mpPaymentId = "mp-" + System.nanoTime();
        var service = new com.red_escuela.backend.billing.usecase.paymentgateway.ProcessMpWebhookService(new com.red_escuela.backend.billing.payment.service.MercadoPagoWebhookRegistration(registrationPort,
                id -> java.util.Optional.of(new com.red_escuela.backend.billing.infrastructure.MercadoPagoApprovedPayment(
                        new ReportedGatewayAllocation(invoice.getId(), invoice.getAmounts().outstandingAmount().value()), 1)),
                new com.red_escuela.backend.billing.infrastructure.MpWebhookSignatureVerifier("", true)));

        service.process("", "req", mpPaymentId);
        entityManager.flush();
        entityManager.clear();

        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPaid();
        assertThat(entityManager.createQuery("select count(p) from com.red_escuela.backend.billing.payment.domain.Payment p where p.gatewayPaymentId = :id", Long.class)
                .setParameter("id", mpPaymentId).getSingleResult()).isEqualTo(1L);
    }

    @Test
    @Tag("D-227")
    void approvingABankTransferReceiptPaysTheAdmissionInvoice() {
        ProgramEnrollmentApplication application = requestApplication("81900002");
        Person preceptor = persistPreceptor();
        approveProgramEnrollmentApplicationUseCase.approve(new ApproveProgramEnrollmentApplicationCommand(application.getId(), preceptor));
        Invoice invoice = application.getAdmissionInvoice();
        entityManager.flush();
        var receipt = uploadPaymentReceiptUseCase.upload(invoice.getId(), application.getStudent().getId(),
                new org.springframework.mock.web.MockMultipartFile("comprobante", "c.pdf", "application/pdf", "%PDF-1.4".getBytes()));
        entityManager.flush();

        approvePaymentReceiptUseCase.approve(receipt.getId(), preceptor, PaymentVerification.none());
        entityManager.flush();
        entityManager.clear();

        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPaid();
    }

    @Test
    @Tag("D-227")
    void requestingAgainWhileAnApprovedApplicationHasTheAdmissionInvoiceUnpaidIsRejected() {
        ProgramEnrollmentApplication application = requestApplication("81900003");
        approveProgramEnrollmentApplicationUseCase.approve(new ApproveProgramEnrollmentApplicationCommand(application.getId(), persistPreceptor()));
        entityManager.flush();

        assertThatThrownBy(() -> requestProgramEnrollmentApplicationUseCase.request(new RequestProgramEnrollmentApplicationCommand(
                application.getStudent(), application.getProgram().getId(), application.getProgramCohort().getId())))
                .isInstanceOf(com.red_escuela.backend.enrollment.exception.AdmissionFeeAwaitingPaymentException.class);
    }

    @Test
    @Tag("D-227")
    void approvingForAStudentWhoIsNotEligibleIsRejectedBeforeIssuingTheAdmissionInvoice() {
        ProgramEnrollmentApplication application = requestApplication("81900004");
        application.getStudent().setOnboardingStatus(StudentOnboardingStatus.PROSPECTO);
        entityManager.flush();

        assertThatThrownBy(() -> approveProgramEnrollmentApplicationUseCase.approve(new ApproveProgramEnrollmentApplicationCommand(application.getId(), persistPreceptor())))
                .isInstanceOf(com.red_escuela.backend.identity.exception.StudentNotConfirmedException.class);
        assertThat(admissionInvoicesOf(application.getStudent())).isZero();
        assertThat(application.getStatus()).isEqualTo(ProgramEnrollmentApplicationStatus.PENDING);
    }

    @Test
    @Tag("D-227")
    void approvingForAStudentAlreadyEnrolledInTheProgramIsRejectedBeforeIssuingTheAdmissionInvoice() {
        ProgramEnrollmentApplication application = requestApplication("81900005");
        programEnrollmentService.enrollAfterAdmissionPayment(application.getStudent().getId(),
                new com.red_escuela.backend.enrollment.dto.CreateProgramEnrollmentRequest(application.getProgram().getId(), application.getProgramCohort().getId()));
        entityManager.flush();

        assertThatThrownBy(() -> approveProgramEnrollmentApplicationUseCase.approve(new ApproveProgramEnrollmentApplicationCommand(application.getId(), persistPreceptor())))
                .isInstanceOf(com.red_escuela.backend.enrollment.exception.AlreadyEnrolledInProgramException.class);
        assertThat(admissionInvoicesOf(application.getStudent())).isZero();
    }

    @Autowired
    private org.springframework.test.context.event.ApplicationEvents applicationEvents;

    @Test
    @Tag("D-184")
    void approvingPublishesAnEventCarryingIdsAndValuesInsteadOfEntities() {
        ProgramEnrollmentApplication application = requestApplication("81900006");

        approveProgramEnrollmentApplicationUseCase.approve(new ApproveProgramEnrollmentApplicationCommand(application.getId(), persistPreceptor()));
        entityManager.flush();

        assertThat(applicationEvents.stream(com.red_escuela.backend.enrollment.events.ProgramApplicationApprovedEvent.class))
                .singleElement().satisfies(event -> {
                    assertThat(event.applicationId()).isEqualTo(application.getId());
                    assertThat(event.studentPersonId()).isEqualTo(application.getStudent().getPerson().getId());
                    assertThat(event.programName()).isEqualTo(application.getProgram().getName());
                    assertThat(event.eventId()).isNotNull();
                });
    }

    @Autowired
    private com.red_escuela.backend.enrollment.service.AdmissionInvoiceReceiptFinder admissionInvoiceReceiptFinder;

    @Test
    @Tag("D-227")
    void theAdmissionInvoiceSummaryExposesTheLatestReceiptStatusWhileItIsUnderReview() {
        ProgramEnrollmentApplication application = requestApplication("81900007");
        approveProgramEnrollmentApplicationUseCase.approve(new ApproveProgramEnrollmentApplicationCommand(application.getId(), persistPreceptor()));
        Invoice invoice = application.getAdmissionInvoice();
        entityManager.flush();
        assertThat(ProgramEnrollmentApplicationResponse.from(application,
                admissionInvoiceReceiptFinder::findLatestReceiptByAdmissionInvoiceId).admissionInvoice().receiptStatus()).isNull();

        uploadPaymentReceiptUseCase.upload(invoice.getId(), application.getStudent().getId(),
                new org.springframework.mock.web.MockMultipartFile("comprobante", "c.pdf", "application/pdf", "%PDF-1.4".getBytes()));
        entityManager.flush();

        ProgramEnrollmentApplicationResponse response = ProgramEnrollmentApplicationResponse.from(application,
                admissionInvoiceReceiptFinder::findLatestReceiptByAdmissionInvoiceId);
        assertThat(response.admissionInvoice().receiptStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(response.admissionInvoice().receiptUploadedAt()).isNotNull();
    }

    @Test
    @Tag("D-231")
    void theApplicationResponseExposesTheLinkedEnrollmentIdOnlyWhenOneExists() {
        ProgramEnrollmentApplication application = requestApplication("81900008");

        assertThat(ProgramEnrollmentApplicationResponse.from(application).programEnrollmentId()).isNull();
        assertThat(ProgramEnrollmentApplicationResponse.from(application, invoiceId -> null, applicationId -> 77L).programEnrollmentId()).isEqualTo(77L);
    }

    @Autowired
    private com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.ListResolvedProgramEnrollmentApplicationsUseCase listResolvedUseCase;

    @Autowired
    private com.red_escuela.backend.enrollment.service.ProgramApplicationReviewViews reviewViews;

    @Autowired
    private GetProgramEnrollmentApplicationApprovalCheckUseCase approvalCheckUseCase;

    @Test
    @Tag("r1-01")
    void approvingTwiceWithTheSameKeyReturnsTheFirstResultWithoutIssuingASecondInvoice() {
        ProgramEnrollmentApplication application = requestApplication("82000001");
        Person preceptor = persistPreceptor();

        ProgramEnrollmentApplication first = approveProgramEnrollmentApplicationUseCase.approve(
                new ApproveProgramEnrollmentApplicationCommand(application.getId(), preceptor).withIdempotencyKey("key-1"));
        ProgramEnrollmentApplication replay = approveProgramEnrollmentApplicationUseCase.approve(
                new ApproveProgramEnrollmentApplicationCommand(application.getId(), preceptor).withIdempotencyKey("key-1"));

        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(replay.getStatus()).isEqualTo(ProgramEnrollmentApplicationStatus.APPROVED);
        assertThat(admissionInvoicesOf(application.getStudent())).isEqualTo(1L);
    }

    @Test
    @Tag("r1-02")
    void approvingTwiceWithADifferentKeyIsStillRejectedAsAlreadyDecided() {
        ProgramEnrollmentApplication application = requestApplication("82000002");
        Person preceptor = persistPreceptor();
        approveProgramEnrollmentApplicationUseCase.approve(new ApproveProgramEnrollmentApplicationCommand(application.getId(), preceptor).withIdempotencyKey("key-1"));

        assertThatThrownBy(() -> approveProgramEnrollmentApplicationUseCase.approve(
                new ApproveProgramEnrollmentApplicationCommand(application.getId(), preceptor).withIdempotencyKey("key-2")))
                .isInstanceOf(InvalidProgramEnrollmentApplicationStateException.class);
        assertThat(admissionInvoicesOf(application.getStudent())).isEqualTo(1L);
    }

    @Test
    @Tag("r1-03")
    void approvingWithTheKeyOfARejectionIsAKeyConflictAndKeepsTheRejection() {
        ProgramEnrollmentApplication application = requestApplication("82000003");
        Person preceptor = persistPreceptor();
        rejectProgramEnrollmentApplicationUseCase.reject(
                new RejectProgramEnrollmentApplicationCommand(application.getId(), preceptor, "Documentación ilegible").withIdempotencyKey("key-1"));

        assertThatThrownBy(() -> approveProgramEnrollmentApplicationUseCase.approve(
                new ApproveProgramEnrollmentApplicationCommand(application.getId(), preceptor).withIdempotencyKey("key-1")))
                .isInstanceOf(com.red_escuela.backend.enrollment.exception.ApplicationDecisionIdempotencyKeyConflictException.class);
        assertThat(entityManager.find(ProgramEnrollmentApplication.class, application.getId()).getStatus())
                .isEqualTo(ProgramEnrollmentApplicationStatus.REJECTED);
    }

    @Test
    @Tag("r1-04")
    void rejectingTwiceWithTheSameKeyIsIdempotentAndRejectingWithTheKeyOfAnApprovalConflicts() {
        ProgramEnrollmentApplication rejected = requestApplication("82000004");
        Person preceptor = persistPreceptor();
        rejectProgramEnrollmentApplicationUseCase.reject(new RejectProgramEnrollmentApplicationCommand(rejected.getId(), preceptor, "Documentación ilegible").withIdempotencyKey("key-1"));

        ProgramEnrollmentApplication replay = rejectProgramEnrollmentApplicationUseCase.reject(
                new RejectProgramEnrollmentApplicationCommand(rejected.getId(), preceptor, "Documentación ilegible").withIdempotencyKey("key-1"));
        assertThat(replay.getStatus()).isEqualTo(ProgramEnrollmentApplicationStatus.REJECTED);

        ProgramEnrollmentApplication approved = requestApplication("82000005");
        approveProgramEnrollmentApplicationUseCase.approve(new ApproveProgramEnrollmentApplicationCommand(approved.getId(), preceptor).withIdempotencyKey("key-2"));
        assertThatThrownBy(() -> rejectProgramEnrollmentApplicationUseCase.reject(
                new RejectProgramEnrollmentApplicationCommand(approved.getId(), preceptor, "Documentación ilegible").withIdempotencyKey("key-2")))
                .isInstanceOf(com.red_escuela.backend.enrollment.exception.ApplicationDecisionIdempotencyKeyConflictException.class);
    }

    @Test
    @Tag("r1-05")
    void aKeyLongerThanTheColumnIsRejectedBeforeTouchingTheApplication() {
        ProgramEnrollmentApplication application = requestApplication("82000006");

        assertThatThrownBy(() -> approveProgramEnrollmentApplicationUseCase.approve(
                new ApproveProgramEnrollmentApplicationCommand(application.getId(), persistPreceptor()).withIdempotencyKey("k".repeat(101))))
                .isInstanceOf(com.red_escuela.backend.enrollment.exception.ApplicationDecisionIdempotencyKeyInvalidException.class);
        assertThat(entityManager.find(ProgramEnrollmentApplication.class, application.getId()).getStatus())
                .isEqualTo(ProgramEnrollmentApplicationStatus.PENDING);
        assertThat(admissionInvoicesOf(application.getStudent())).isZero();
    }

    @Test
    @Tag("r1-05")
    void aKeyOfExactlyTheMaximumLengthIsAccepted() {
        ProgramEnrollmentApplication application = requestApplication("82000007");

        ProgramEnrollmentApplication approved = approveProgramEnrollmentApplicationUseCase.approve(
                new ApproveProgramEnrollmentApplicationCommand(application.getId(), persistPreceptor()).withIdempotencyKey("k".repeat(100)));
        entityManager.flush();

        assertThat(approved.getStatus()).isEqualTo(ProgramEnrollmentApplicationStatus.APPROVED);
    }

    @Test
    @Tag("r1-06")
    void requestingAnApplicationPublishesAReceivedEventWithTheStudentAndProgramNames() {
        ProgramEnrollmentApplication application = requestApplication("82000008");

        assertThat(applicationEvents.stream(com.red_escuela.backend.enrollment.events.ProgramApplicationReceivedEvent.class))
                .singleElement().satisfies(event -> {
                    assertThat(event.applicationId()).isEqualTo(application.getId());
                    assertThat(event.studentFullName()).isEqualTo("Persona 82000008");
                    assertThat(event.programName()).isEqualTo(application.getProgram().getName());
                });
    }

    @Test
    @Tag("r1-07")
    void theReviewViewExposesTheStudentCohortAndWhichDocumentTypesAreMissing() {
        ProgramEnrollmentApplication application = requestApplicationWithoutDocuments("82000009", DocumentType.DNI_DORSO);
        application = entityManager.find(ProgramEnrollmentApplication.class, application.getId());

        ProgramEnrollmentApplicationResponse view = reviewViews.viewOfApplication(application.getId());

        assertThat(view.studentFullName()).isEqualTo("Persona 82000009");
        assertThat(view.studentDni()).isEqualTo("82000009");
        assertThat(view.studentEmail()).isEqualTo("82000009@example.com");
        assertThat(view.programCohortName()).startsWith("FIRST ");
        assertThat(view.decidedByName()).isNull();
        assertThat(view.documentation().complete()).isFalse();
        assertThat(view.documentation().missingTypes()).containsExactly(DocumentType.DNI_DORSO);
        assertThat(view.documentation().documents()).extracting(document -> document.type())
                .containsExactlyInAnyOrder(DocumentType.DNI_FRENTE, DocumentType.ANALITICO_SECUNDARIO);
    }

    @Test
    @Tag("r1-07")
    void theReviewViewNamesWhoDecidedOnceTheApplicationIsResolved() {
        ProgramEnrollmentApplication application = requestApplication("82000010");
        Person preceptor = persistPreceptor();
        preceptor.setName("Marta");
        preceptor.setLastname("Gómez");

        ProgramEnrollmentApplication approved = approveProgramEnrollmentApplicationUseCase.approve(
                new ApproveProgramEnrollmentApplicationCommand(application.getId(), preceptor));

        assertThat(reviewViews.viewOfApplication(approved.getId()).decidedByName()).isEqualTo("Marta Gómez");
        assertThat(reviewViews.viewOfApplication(approved.getId()).documentation().complete()).isTrue();
    }

    @Test
    @Tag("r1-08")
    void theHistoryListsOnlyResolvedApplicationsNewestDecisionFirstAndPaginatesWithTheTotal() {
        Person preceptor = persistPreceptor();
        ProgramEnrollmentApplication pending = requestApplication("82000011");
        ProgramEnrollmentApplication approved = requestApplication("82000012");
        ProgramEnrollmentApplication rejected = requestApplication("82000013");
        approveProgramEnrollmentApplicationUseCase.approve(new ApproveProgramEnrollmentApplicationCommand(approved.getId(), preceptor));
        rejectProgramEnrollmentApplicationUseCase.reject(new RejectProgramEnrollmentApplicationCommand(rejected.getId(), preceptor, "Documentación ilegible"));
        entityManager.flush();
        entityManager.createQuery("update ProgramEnrollmentApplication a set a.decidedAt = :at where a.id = :id")
                .setParameter("at", java.time.LocalDateTime.now().minusDays(1)).setParameter("id", approved.getId()).executeUpdate();
        entityManager.clear();

        var firstPage = listResolvedUseCase.list(null, "8200001", 0, 1);
        var secondPage = listResolvedUseCase.list(null, "8200001", 1, 1);
        var onlyRejected = listResolvedUseCase.list("REJECTED", "8200001", 0, 10);

        assertThat(firstPage.totalElements()).isEqualTo(2);
        assertThat(firstPage.items()).extracting(ProgramEnrollmentApplicationResponse::id).containsExactly(rejected.getId());
        assertThat(secondPage.items()).extracting(ProgramEnrollmentApplicationResponse::id).containsExactly(approved.getId());
        assertThat(onlyRejected.items()).extracting(ProgramEnrollmentApplicationResponse::id).containsExactly(rejected.getId());
        assertThat(onlyRejected.items().getFirst().decidedByName()).isEqualTo("Persona " + preceptor.getDni());
        assertThat(listResolvedUseCase.list(null, "8200001", 0, 10).items()).extracting(ProgramEnrollmentApplicationResponse::id)
                .doesNotContain(pending.getId());
    }

    @Test
    @Tag("r1-09")
    void theHistorySearchMatchesTheStudentNameOrDniAndTreatsWildcardsAsPlainText() {
        Person preceptor = persistPreceptor();
        ProgramEnrollmentApplication rejected = requestApplication("82000014");
        rejectProgramEnrollmentApplicationUseCase.reject(new RejectProgramEnrollmentApplicationCommand(rejected.getId(), preceptor, "Documentación ilegible"));
        entityManager.flush();

        assertThat(listResolvedUseCase.list(null, "82000014", 0, 10).items()).extracting(ProgramEnrollmentApplicationResponse::id).contains(rejected.getId());
        assertThat(listResolvedUseCase.list(null, "persona 82000014", 0, 10).items()).extracting(ProgramEnrollmentApplicationResponse::id).contains(rejected.getId());
        assertThat(listResolvedUseCase.list(null, "%", 0, 10).items()).isEmpty();
        assertThat(listResolvedUseCase.list(null, "_2000014", 0, 10).items()).isEmpty();
    }

    @Test
    @Tag("r1-10")
    void theHistoryRefusesToListPendingApplicationsAndCapsThePageSize() {
        assertThatThrownBy(() -> listResolvedUseCase.list("PENDING", null, 0, 10))
                .isInstanceOf(com.red_escuela.backend.enrollment.exception.InvalidResolvedApplicationStatusException.class);
        assertThat(listResolvedUseCase.list(null, null, -5, 100000)).isNotNull();
    }

    @Test
    @Tag("r1-11")
    void theApprovalCheckReportsMissingDocumentsAndNoDebtForAStudentWithoutInvoices() {
        ProgramEnrollmentApplication application = requestApplicationWithoutDocuments("82000015", DocumentType.ANALITICO_SECUNDARIO);

        var check = approvalCheckUseCase.get(application.getId());

        assertThat(check.canApprove()).isFalse();
        assertThat(check.documentation().missingTypes()).containsExactly(DocumentType.ANALITICO_SECUNDARIO);
        assertThat(check.debt().blocksApproval()).isFalse();
        assertThat(check.debt().violationCodes()).isEmpty();
    }

    @Test
    @Tag("r1-11")
    void theApprovalCheckSaysTheApplicationCanBeApprovedWhenDocumentsAreCompleteAndThereIsNoDebt() {
        ProgramEnrollmentApplication application = requestApplication("82000016");

        assertThat(approvalCheckUseCase.get(application.getId()).canApprove()).isTrue();
    }

    @Autowired
    private ListPendingProgramEnrollmentApplicationsForReviewUseCase listPendingForReviewUseCase;

    @Test
    @Tag("r1-12")
    void thePendingListForReviewCarriesStudentCohortAndDocumentationAndLeavesOutResolvedOnes() {
        ProgramEnrollmentApplication pending = requestApplicationWithoutDocuments("82000017", DocumentType.DNI_FRENTE);
        ProgramEnrollmentApplication approved = requestApplication("82000018");
        approveProgramEnrollmentApplicationUseCase.approve(new ApproveProgramEnrollmentApplicationCommand(approved.getId(), persistPreceptor()));
        entityManager.flush();
        entityManager.clear();

        List<ProgramEnrollmentApplicationResponse> views = listPendingForReviewUseCase.list();

        assertThat(views).extracting(ProgramEnrollmentApplicationResponse::id).contains(pending.getId()).doesNotContain(approved.getId());
        ProgramEnrollmentApplicationResponse view = views.stream().filter(candidate -> candidate.id().equals(pending.getId())).findFirst().orElseThrow();
        assertThat(view.studentFullName()).isEqualTo("Persona 82000017");
        assertThat(view.programCohortName()).startsWith("FIRST ");
        assertThat(view.documentation().missingTypes()).containsExactly(DocumentType.DNI_FRENTE);
    }
}
