package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.infrastructure.MercadoPagoApprovedPayment;
import com.red_escuela.backend.billing.infrastructure.MpWebhookSignatureVerifier;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.payment.domain.CardPaymentDetails;
import com.red_escuela.backend.billing.payment.domain.CardPaymentType;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import com.red_escuela.backend.billing.payment.domain.PaymentStatus;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.billing.payment.service.ApprovedPaymentCreditService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.usecase.paymentgateway.ProcessMpWebhookService;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.billing.exception.InvalidMpWebhookSignatureException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static com.red_escuela.backend.billing.support.InvoiceAssertions.assertThatInvoice;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@org.springframework.test.context.event.RecordApplicationEvents
class ProcessMpWebhookServiceTest extends AbstractIntegrationTest {
    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private StudentCurrentAccountService accountService;

    @Autowired
    private ApprovedPaymentCreditService creditService;

    @Autowired
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Autowired
    private java.time.Clock clock;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private com.red_escuela.backend.billing.invoice.service.InvoiceViewLoader invoiceViewLoader;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @Autowired
    private TermRepository termRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private org.springframework.test.context.event.ApplicationEvents applicationEvents;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private com.red_escuela.backend.billing.invariant.BillingInvariantChecker invariants;

    @org.junit.jupiter.api.BeforeEach
    void startBillingInvariantBaseline() {
        invariants = com.red_escuela.backend.billing.invariant.BillingInvariantChecker.startingNow(jdbc);
    }

    private Invoice persistPendingInvoice(BigDecimal amount) {
        Address address = addressRepository.save(new Address());
        Person person = new Person();
        String username = "mp.webhook." + System.nanoTime();
        person.setUsername(username);
        person.setDni(Long.toString(Math.abs((long) (username).hashCode())));
        person.setName("Persona");
        person.setLastname("Prueba");
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        person = personRepository.save(person);

        Institution institution = new Institution();
        institution.setName("Instituto de prueba " + System.nanoTime());
        institution = institutionRepository.save(institution);

        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        student = studentRepository.save(student);

        Term term = new Term();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        term = termRepository.save(term);

        Course course = new Course();
        course.setName("Curso de prueba " + System.nanoTime());
        course.setTerm(term);
        course.setIsActive(StatusType.ACTIVE);
        course = courseRepository.save(course);

        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(student);
        enrollment.setCourse(course);
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        enrollment = courseEnrollmentRepository.save(enrollment);

        Invoice invoice = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(YearMonth.now()).dueOn(LocalDate.now()).withBaseAmount(ars(amount)).build();
        return invoice;
    }

    private ProcessMpWebhookService serviceFetching(com.red_escuela.backend.billing.infrastructure.MercadoPagoPaymentFetcher fetcher, MpWebhookSignatureVerifier verifier) {
        return new ProcessMpWebhookService(invoiceRepository, paymentRepository, accountService, creditService, fetcher, verifier, eventPublisher, clock);
    }

    private ProcessMpWebhookService serviceReporting(Long invoiceId, BigDecimal reportedAmount) {
        return serviceFetching(mpPaymentId -> Optional.of(new MercadoPagoApprovedPayment(new PaymentAllocationRequest(invoiceId, ars(reportedAmount)), 1)),
                new MpWebhookSignatureVerifier("", true));
    }

    private List<Payment> mpPayments(String mpPaymentId) {
        List<Long> ids = jdbc.queryForList("select id from payment where gateway = 'MERCADO_PAGO' and gateway_payment_id = ?", Long.class, mpPaymentId);
        return paymentRepository.findByIdIn(ids);
    }

    private long activeAllocationsOf(Long invoiceId) {
        return jdbc.queryForObject("select count(*) from payment_allocation a where a.invoice_id = ? and not exists "
                + "(select 1 from payment_allocation r where r.reverses_allocation_id = a.id) and a.reverses_allocation_id is null", Long.class, invoiceId);
    }

    @Test
    @Tag("D-139")
    @Tag("D-136")
    @Tag("D-144")
    void aMercadoPagoAmountBelowTheBalanceIsCreditedToTheAccountAndTheInvoiceStaysPending() {
        Invoice invoice = persistPendingInvoice(BigDecimal.valueOf(10000));
        ProcessMpWebhookService service = serviceReporting(invoice.getId(), BigDecimal.valueOf(4000));
        String mpPaymentId = String.valueOf(System.nanoTime());

        transactionTemplate.executeWithoutResult(status -> service.process("", "request-1", mpPaymentId));

        assertThat(activeAllocationsOf(invoice.getId())).isZero();
        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPending();
        assertThat(mpPayments(mpPaymentId)).singleElement()
                .satisfies(payment -> assertThat(payment.getAmount().value()).isEqualTo(ars(BigDecimal.valueOf(4000))));
        Long accountId = invoiceRepository.findById(invoice.getId()).orElseThrow().getStudentCurrentAccountId();
        assertThat(jdbc.queryForList("select entry_type, direction, amount from current_account_entry where student_current_account_id = ? order by sequence_number", accountId))
                .extracting(row -> row.get("entry_type") + ":" + row.get("direction") + ":" + ((BigDecimal) row.get("amount")).setScale(0))
                .containsExactly("INVOICE_ISSUED:DEBIT:10000", "PAYMENT_RECEIVED:CREDIT:4000");
        assertThat(jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = ?", BigDecimal.class, accountId))
                .isEqualByComparingTo("4000");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-139")
    void aSecondMercadoPagoAmountThatCompletesTheBalanceSettlesTheInvoiceWithoutLeftovers() {
        Invoice invoice = persistPendingInvoice(BigDecimal.valueOf(10000));

        transactionTemplate.executeWithoutResult(status -> serviceReporting(invoice.getId(), BigDecimal.valueOf(4000))
                .process("", "request-1", "first-" + System.nanoTime()));
        transactionTemplate.executeWithoutResult(status -> serviceReporting(invoice.getId(), BigDecimal.valueOf(6000))
                .process("", "request-2", "second-" + System.nanoTime()));

        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPaid();
        Long accountId = invoiceRepository.findById(invoice.getId()).orElseThrow().getStudentCurrentAccountId();
        assertThat(jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = ?", BigDecimal.class, accountId))
                .isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("select count(*) from current_account_entry where student_current_account_id = ? and entry_type = 'PAYMENT_RECEIVED'", Long.class, accountId))
                .isEqualTo(2L);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-376")
    @Tag("D-256")
    void aMercadoPagoPaymentSettlingAnInvoiceLeavesItsAllocationSourcePointingToTheRegisteredPayment() {
        Invoice invoice = persistPendingInvoice(BigDecimal.valueOf(10000));
        String mpPaymentId = "source-" + System.nanoTime();
        long legacyPaymentsBefore = jdbc.queryForObject("select count(*) from legacy_payment", Long.class);

        transactionTemplate.executeWithoutResult(status -> serviceReporting(invoice.getId(), BigDecimal.valueOf(10000)).process("", "request-1", mpPaymentId));

        Long paymentId = mpPayments(mpPaymentId).get(0).getId();
        assertThat(jdbc.queryForList("select s.payment_id, s.amount from payment_allocation_source s join payment_allocation a on a.id = s.payment_allocation_id where a.invoice_id = ?",
                invoice.getId())).singleElement().satisfies(source -> {
                    assertThat(source.get("payment_id")).isEqualTo(paymentId);
                    assertThat((BigDecimal) source.get("amount")).isEqualByComparingTo("10000");
                });
        assertThat(jdbc.queryForObject("select count(*) from legacy_payment", Long.class)).isEqualTo(legacyPaymentsBefore);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-079")
    void theWebhookStoresTheCardTypeAndInstallmentsReportedByTheGateway() {
        Invoice invoice = persistPendingInvoice(BigDecimal.valueOf(10000));
        String mpPaymentId = String.valueOf(System.nanoTime());
        ProcessMpWebhookService service = serviceFetching(id -> Optional.of(new MercadoPagoApprovedPayment(
                        new PaymentAllocationRequest(invoice.getId(), ars(BigDecimal.valueOf(10000))), 6, CardPaymentType.CREDIT)),
                new MpWebhookSignatureVerifier("", true));

        transactionTemplate.executeWithoutResult(status -> service.process("", "request-1", mpPaymentId));

        assertThat(mpPayments(mpPaymentId)).singleElement().satisfies(payment -> {
            assertThat(payment.getGateway()).isEqualTo(PaymentGatewayCode.MERCADO_PAGO);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(payment.getGatewayRawStatus()).isEqualTo("approved");
            assertThat(payment.getMethodDetails()).isEqualTo(new CardPaymentDetails(CardPaymentType.CREDIT, 6, null));
        });
    }

    @Test
    @Tag("D-139")
    @Tag("D-144")
    void aMercadoPagoAmountAboveTheBalanceSettlesTheInvoiceAndKeepsTheExcessAsAccountCredit() {
        Invoice invoice = persistPendingInvoice(BigDecimal.valueOf(10000));
        ProcessMpWebhookService service = serviceReporting(invoice.getId(), BigDecimal.valueOf(15000));
        String mpPaymentId = String.valueOf(System.nanoTime());

        transactionTemplate.executeWithoutResult(status -> service.process("", "request-1", mpPaymentId));

        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPaid();
        Long accountId = invoiceRepository.findById(invoice.getId()).orElseThrow().getStudentCurrentAccountId();
        assertThat(jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = ?", BigDecimal.class, accountId))
                .isEqualByComparingTo("5000");
        assertThat(mpPayments(mpPaymentId)).singleElement()
                .satisfies(payment -> assertThat(payment.getAmount().value()).isEqualTo(ars(BigDecimal.valueOf(15000))));
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-139")
    @Tag("D-144")
    void aMercadoPagoPaymentOnAnAlreadyPaidInvoiceIsCreditedToTheAccountInsteadOfBeingIgnored() {
        Invoice invoice = persistPendingInvoice(BigDecimal.valueOf(10000));
        transactionTemplate.executeWithoutResult(status -> serviceReporting(invoice.getId(), BigDecimal.valueOf(10000))
                .process("", "request-1", "first-" + System.nanoTime()));
        String secondPaymentId = "second-" + System.nanoTime();

        transactionTemplate.executeWithoutResult(status -> serviceReporting(invoice.getId(), BigDecimal.valueOf(10000))
                .process("", "request-2", secondPaymentId));

        assertThat(mpPayments(secondPaymentId)).hasSize(1);
        Long accountId = invoiceRepository.findById(invoice.getId()).orElseThrow().getStudentCurrentAccountId();
        assertThat(jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = ?", BigDecimal.class, accountId))
                .isEqualByComparingTo("10000");
        assertThat(activeAllocationsOf(invoice.getId())).isEqualTo(1);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-139")
    @Tag("D-144")
    void twoConcurrentWebhooksWithDifferentIdsOnTheSameInvoiceLoseNoMoneyAndAllocateOnce() throws Exception {
        Invoice invoice = persistPendingInvoice(BigDecimal.valueOf(10000));
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String mpPaymentId = "concurrent-" + i + "-" + System.nanoTime();
                futures.add(executor.submit(() -> {
                    start.await();
                    transactionTemplate.executeWithoutResult(status -> serviceReporting(invoice.getId(), BigDecimal.valueOf(10000))
                            .process("", "request-" + mpPaymentId, mpPaymentId));
                    return null;
                }));
            }
            start.countDown();
            for (java.util.concurrent.Future<?> future : futures) {
                future.get(60, java.util.concurrent.TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        Long accountId = invoiceRepository.findById(invoice.getId()).orElseThrow().getStudentCurrentAccountId();
        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPaid();
        assertThat(activeAllocationsOf(invoice.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select coalesce(sum(amount), 0) from current_account_entry where student_current_account_id = ? and entry_type = 'PAYMENT_RECEIVED'", BigDecimal.class, accountId))
                .isEqualByComparingTo("20000");
        assertThat(jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = ?", BigDecimal.class, accountId))
                .isEqualByComparingTo("10000");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-139")
    @Tag("D-183")
    void twoSimultaneousDeliveriesOfTheSameMercadoPagoPaymentCreditItOnceAndBothFinishWithoutError() throws Exception {
        Invoice invoice = persistPendingInvoice(BigDecimal.valueOf(10000));
        String mpPaymentId = "same-delivery-" + System.nanoTime();
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    transactionTemplate.executeWithoutResult(status -> serviceReporting(invoice.getId(), BigDecimal.valueOf(10000))
                            .process("", "request-" + System.nanoTime(), mpPaymentId));
                    return null;
                }));
            }
            start.countDown();
            for (java.util.concurrent.Future<?> future : futures) {
                future.get(60, java.util.concurrent.TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        Long accountId = invoiceRepository.findById(invoice.getId()).orElseThrow().getStudentCurrentAccountId();
        assertThat(mpPayments(mpPaymentId)).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from current_account_entry where student_current_account_id = ? and entry_type = 'PAYMENT_RECEIVED'", Long.class, accountId))
                .isEqualTo(1L);
        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPaid();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-134")
    void aMercadoPagoPaymentOnAnInvoiceThatIsNoLongerOpenStaysAsAccountCreditAndAnnouncesIt() {
        Invoice invoice = persistPendingInvoice(BigDecimal.valueOf(10000));
        transactionTemplate.executeWithoutResult(status -> serviceReporting(invoice.getId(), BigDecimal.valueOf(10000))
                .process("", "request-1", "first-" + System.nanoTime()));
        applicationEvents.clear();

        transactionTemplate.executeWithoutResult(status -> serviceReporting(invoice.getId(), BigDecimal.valueOf(10000))
                .process("", "request-2", "second-" + System.nanoTime()));

        assertThat(applicationEvents.stream(com.red_escuela.backend.billing.events.PaymentLeftAsAccountCreditEvent.class)).hasSize(1)
                .allSatisfy(event -> assertThat(event.reportedInvoiceId()).isEqualTo(invoice.getId()));
    }

    @Test
    @Tag("D-134")
    void aMercadoPagoPaymentOnAnOpenInvoiceDoesNotAnnounceAnyLeftoverCredit() {
        Invoice invoice = persistPendingInvoice(BigDecimal.valueOf(10000));
        applicationEvents.clear();

        transactionTemplate.executeWithoutResult(status -> serviceReporting(invoice.getId(), BigDecimal.valueOf(10000))
                .process("", "request-1", "only-" + System.nanoTime()));

        assertThat(applicationEvents.stream(com.red_escuela.backend.billing.events.PaymentLeftAsAccountCreditEvent.class)).isEmpty();
        assertThat(applicationEvents.stream(com.red_escuela.backend.billing.events.AccountCreditAllocatedEvent.class)).hasSize(1);
    }

    private Invoice persistSiblingPendingInvoice(Invoice sibling, BigDecimal amount) {
        CourseEnrollment enrollment = courseEnrollmentRepository.findById(invoiceRepository.findInvoiceWithLinesById(sibling.getId()).orElseThrow()
                .findCourseEnrollmentId().orElseThrow()).orElseThrow();
        return BillingTestFixtures.invoice().forCourseEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(YearMonth.now().plusMonths(1))
                .dueOn(LocalDate.now().plusDays(30)).withBaseAmount(ars(amount)).build();
    }

    @Test
    @Tag("D-134")
    void aMercadoPagoPaymentOnAClosedInvoiceThatSettlesAnotherOpenInvoiceDoesNotAnnounceLeftoverCredit() {
        Invoice reported = persistPendingInvoice(BigDecimal.valueOf(10000));
        transactionTemplate.executeWithoutResult(status -> serviceReporting(reported.getId(), BigDecimal.valueOf(10000))
                .process("", "request-1", "first-" + System.nanoTime()));
        Invoice other = persistSiblingPendingInvoice(reported, BigDecimal.valueOf(10000));
        applicationEvents.clear();

        transactionTemplate.executeWithoutResult(status -> serviceReporting(reported.getId(), BigDecimal.valueOf(10000))
                .process("", "request-2", "second-" + System.nanoTime()));

        assertThatInvoice(invoiceRepository.findById(other.getId()).orElseThrow()).isPaid();
        assertThat(applicationEvents.stream(com.red_escuela.backend.billing.events.PaymentLeftAsAccountCreditEvent.class)).isEmpty();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-134")
    void aMercadoPagoPaymentSmallerThanAnOpenInvoiceAnnouncesTheCreditThatStaysInFavor() {
        Invoice invoice = persistPendingInvoice(BigDecimal.valueOf(10000));
        applicationEvents.clear();

        transactionTemplate.executeWithoutResult(status -> serviceReporting(invoice.getId(), BigDecimal.valueOf(4000))
                .process("", "request-1", "smaller-" + System.nanoTime()));

        assertThat(applicationEvents.stream(com.red_escuela.backend.billing.events.PaymentLeftAsAccountCreditEvent.class)).singleElement()
                .satisfies(event -> assertThat(event.amount()).isEqualByComparingTo("4000"));
    }

    @Test
    void anApprovedPaymentLogsInfoWithPaymentAndInvoiceIds(CapturedOutput output) {
        Invoice invoice = persistPendingInvoice(BigDecimal.valueOf(10000));
        String mpPaymentId = String.valueOf(System.nanoTime());
        ProcessMpWebhookService service = serviceReporting(invoice.getId(), BigDecimal.valueOf(10000));

        transactionTemplate.executeWithoutResult(status -> service.process("", "request-1", mpPaymentId));

        assertThat(output.getOut())
                .contains("Webhook de Mercado Pago acreditado")
                .contains("pagoMp=" + mpPaymentId)
                .contains("factura=" + invoice.getId());
    }

    @Test
    void anInvalidSignatureLogsWarnWithoutLeakingTheSecretOrTheSignature(CapturedOutput output) {
        String secret = "super-secret-webhook-key";
        String signature = "ts=1,v1=deadbeefdeadbeef";
        ProcessMpWebhookService service = serviceFetching(mpPaymentId -> Optional.empty(), new MpWebhookSignatureVerifier(secret, false));

        assertThatThrownBy(() -> service.process(signature, "request-1", "123456"))
                .isInstanceOf(InvalidMpWebhookSignatureException.class);

        assertThat(output.getOut())
                .contains("WARN")
                .contains("Webhook de Mercado Pago rechazado por firma inválida")
                .doesNotContain(secret)
                .doesNotContain("deadbeefdeadbeef");
    }

    @Test
    void theWebhookRegistersThePaymentUnderTheAnonymousAuthenticationOfAPublicEndpoint() {
        Invoice invoice = persistPendingInvoice(BigDecimal.valueOf(10000));
        ProcessMpWebhookService service = serviceReporting(invoice.getId(), BigDecimal.valueOf(10000));
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "anonymous", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        try {
            transactionTemplate.executeWithoutResult(status -> service.process("", "request-1", String.valueOf(System.nanoTime())));
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPaid();
    }

    @Test
    @Tag("D-139")
    void theSameMercadoPagoPaymentReportedTwiceSequentiallyIsCreditedOnce() {
        Invoice invoice = persistPendingInvoice(BigDecimal.valueOf(10000));
        String mpPaymentId = "same-" + System.nanoTime();
        transactionTemplate.executeWithoutResult(status -> serviceReporting(invoice.getId(), BigDecimal.valueOf(4000)).process("", "request-1", mpPaymentId));
        transactionTemplate.executeWithoutResult(status -> serviceReporting(invoice.getId(), BigDecimal.valueOf(4000)).process("", "request-2", mpPaymentId));

        Long accountId = invoiceRepository.findById(invoice.getId()).orElseThrow().getStudentCurrentAccountId();
        assertThat(mpPayments(mpPaymentId)).hasSize(1);
        assertThat(jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = ?", BigDecimal.class, accountId)).isEqualByComparingTo("4000");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-139")
    void theSameMercadoPagoPaymentReportedTwiceConcurrentlyIsCreditedOnce() throws Exception {
        Invoice invoice = persistPendingInvoice(BigDecimal.valueOf(10000));
        String mpPaymentId = "concurrent-" + System.nanoTime();
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.List<java.util.concurrent.Future<?>> deliveries = new java.util.ArrayList<>();
        for (int delivery = 0; delivery < 2; delivery++) {
            deliveries.add(executor.submit(() -> {
                start.await();
                transactionTemplate.executeWithoutResult(status -> serviceReporting(invoice.getId(), BigDecimal.valueOf(10000)).process("", "request", mpPaymentId));
                return null;
            }));
        }
        start.countDown();
        java.util.List<Throwable> rejectedDeliveries = new java.util.ArrayList<>();
        for (java.util.concurrent.Future<?> delivery : deliveries) {
            try {
                delivery.get(60, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException e) {
                rejectedDeliveries.add(e.getCause());
            }
        }
        executor.shutdownNow();
        assertThat(rejectedDeliveries).hasSizeLessThanOrEqualTo(1)
                .allSatisfy(failure -> assertThat(failure).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class));

        Long accountId = invoiceRepository.findById(invoice.getId()).orElseThrow().getStudentCurrentAccountId();
        assertThat(mpPayments(mpPaymentId)).hasSize(1);
        assertThat(jdbc.queryForObject("select coalesce(sum(amount),0) from current_account_entry where student_current_account_id = ? and entry_type = 'PAYMENT_RECEIVED'", BigDecimal.class, accountId)).isEqualByComparingTo("10000");
        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPaid();
        assertThat(activeAllocationsOf(invoice.getId())).isEqualTo(1);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-134")
    void aMercadoPagoPaymentOnACancelledInvoiceIsCreditedToTheAccountAndNotAllocated() {
        Invoice invoice = persistPendingInvoice(BigDecimal.valueOf(10000));
        jdbc.update("update invoice set payment_status = 'CANCELLED', outstanding_amount = 0, paid_amount = 0, credited_amount = total where id = ?", invoice.getId());
        String mpPaymentId = "cancelled-" + System.nanoTime();

        transactionTemplate.executeWithoutResult(status -> serviceReporting(invoice.getId(), BigDecimal.valueOf(10000)).process("", "request", mpPaymentId));

        Long accountId = invoiceRepository.findById(invoice.getId()).orElseThrow().getStudentCurrentAccountId();
        assertThat(activeAllocationsOf(invoice.getId())).isZero();
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoice.getId())).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = ?", BigDecimal.class, accountId)).isEqualByComparingTo("10000");
    }

    @Test
    @Tag("D-139")
    void aPriorCreditPlusAShortMercadoPagoPaymentSettlesTheReportedInvoice() {
        Invoice invoice = persistPendingInvoice(BigDecimal.valueOf(10000));
        CourseEnrollment enrollment = transactionTemplate.execute(status -> {
            CourseEnrollment loaded = invoiceViewLoader.loadInvoiceView(invoiceRepository.findById(invoice.getId()).orElseThrow()).courseEnrollment();
            loaded.getStudent().getId();
            return loaded;
        });
        Invoice otherInvoice = BillingTestFixtures.invoice().forCourseEnrollment(enrollment)
                .ofType(FeeType.ADMINISTRATIVE_FEE).dueOn(LocalDate.now()).withBaseAmount(ars(BigDecimal.valueOf(3000))).build();
        transactionTemplate.executeWithoutResult(status -> serviceReporting(otherInvoice.getId(), BigDecimal.valueOf(6000)).process("", "request-1", "first-" + System.nanoTime()));

        transactionTemplate.executeWithoutResult(status -> serviceReporting(invoice.getId(), BigDecimal.valueOf(7000)).process("", "request-2", "second-" + System.nanoTime()));

        assertThatInvoice(invoiceRepository.findById(otherInvoice.getId()).orElseThrow()).isPaid();
        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPaid();
        Long accountId = invoiceRepository.findById(invoice.getId()).orElseThrow().getStudentCurrentAccountId();
        assertThat(jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = ?", BigDecimal.class, accountId)).isEqualByComparingTo("0");
        invariants.assertAllBillingInvariantsHold();
    }
}
