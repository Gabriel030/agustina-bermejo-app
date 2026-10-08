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
import com.red_escuela.backend.billing.entity.PaymentAllocation;
import com.red_escuela.backend.billing.exception.PaymentExceedsInvoiceBalanceException;
import com.red_escuela.backend.billing.infrastructure.MercadoPagoApprovedPayment;
import com.red_escuela.backend.billing.infrastructure.MpWebhookSignatureVerifier;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PaymentAllocationRepository;
import com.red_escuela.backend.billing.repository.PaymentRepository;
import com.red_escuela.backend.billing.usecase.payment.RegisterPaymentUseCase;
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
import java.util.Optional;

import static com.red_escuela.backend.billing.support.InvoiceAssertions.assertThatInvoice;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ProcessMpWebhookServiceTest extends AbstractIntegrationTest {
    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentAllocationRepository paymentAllocationRepository;

    @Autowired
    private RegisterPaymentUseCase registerPaymentUseCase;

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

    private ProcessMpWebhookService serviceReporting(Long invoiceId, BigDecimal reportedAmount) {
        return new ProcessMpWebhookService(invoiceRepository, invoiceViewLoader, paymentRepository, registerPaymentUseCase,
                mpPaymentId -> Optional.of(new MercadoPagoApprovedPayment(new PaymentAllocationRequest(invoiceId, ars(reportedAmount)), 1)),
                new MpWebhookSignatureVerifier("", true));
    }

    @Test
    void theWebhookRecordsTheAmountReportedByMercadoPagoNotTheInvoiceBalance() {
        Invoice invoice = persistPendingInvoice(BigDecimal.valueOf(10000));
        String mpPaymentId = String.valueOf(System.nanoTime());

        ProcessMpWebhookService service = serviceReporting(invoice.getId(), BigDecimal.valueOf(4000));

        transactionTemplate.executeWithoutResult(status -> service.process("", "request-1", mpPaymentId));

        assertThat(paymentAllocationRepository.findByInvoiceId(invoice.getId()))
                .extracting(PaymentAllocation::getAmountApplied)
                .singleElement()
                .satisfies(amount -> assertThat(amount).isEqualTo(ars(4000)));
        assertThatInvoice(invoiceRepository.findById(invoice.getId()).orElseThrow()).isPending();
    }

    @Test
    @Tag("D-079")
    void theWebhookStoresTheCardTypeAndInstallmentsReportedByTheGateway() {
        Invoice invoice = persistPendingInvoice(BigDecimal.valueOf(10000));
        String mpPaymentId = String.valueOf(System.nanoTime());
        ProcessMpWebhookService service = new ProcessMpWebhookService(invoiceRepository, invoiceViewLoader, paymentRepository, registerPaymentUseCase,
                id -> Optional.of(new MercadoPagoApprovedPayment(
                        new PaymentAllocationRequest(invoice.getId(), ars(BigDecimal.valueOf(10000))), 6,
                        com.red_escuela.backend.billing.entity.CardType.CREDIT)),
                new MpWebhookSignatureVerifier("", true));

        transactionTemplate.executeWithoutResult(status -> service.process("", "request-1", mpPaymentId));

        assertThat(paymentRepository.findAll()).filteredOn(p -> mpPaymentId.equals(p.getExternalReference()))
                .singleElement().satisfies(payment -> {
                    assertThat(payment.getCardType()).isEqualTo(com.red_escuela.backend.billing.entity.CardType.CREDIT);
                    assertThat(payment.getCardInstallments()).isEqualTo(6);
                });
    }

    @Test
    void aMercadoPagoAmountAboveTheBalanceKeepsTheOverpaymentRule() {
        Invoice invoice = persistPendingInvoice(BigDecimal.valueOf(10000));
        ProcessMpWebhookService service = serviceReporting(invoice.getId(), BigDecimal.valueOf(15000));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                status -> service.process("", "request-1", String.valueOf(System.nanoTime()))))
                .isInstanceOf(PaymentExceedsInvoiceBalanceException.class);
        assertThat(paymentAllocationRepository.findByInvoiceId(invoice.getId())).isEmpty();
    }
    @Test
    void anApprovedPaymentLogsInfoWithPaymentAndInvoiceIds(CapturedOutput output) {
        Invoice invoice = persistPendingInvoice(BigDecimal.valueOf(10000));
        String mpPaymentId = String.valueOf(System.nanoTime());
        ProcessMpWebhookService service = serviceReporting(invoice.getId(), BigDecimal.valueOf(4000));

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
        ProcessMpWebhookService service = new ProcessMpWebhookService(invoiceRepository, invoiceViewLoader, paymentRepository, registerPaymentUseCase,
                mpPaymentId -> Optional.empty(), new MpWebhookSignatureVerifier(secret, false));

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
}
