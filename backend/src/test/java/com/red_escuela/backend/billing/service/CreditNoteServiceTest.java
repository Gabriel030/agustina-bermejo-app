package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.billing.dto.CreateCreditNoteRequest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.billing.entity.CreditNote;
import com.red_escuela.backend.billing.entity.CreditNoteReasonType;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.Payment;
import com.red_escuela.backend.billing.entity.PaymentMethod;
import com.red_escuela.backend.billing.entity.PaymentStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.billing.exception.InsufficientCreditBalanceException;
import com.red_escuela.backend.billing.exception.InvalidCreditNoteReasonException;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PaymentRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@ActiveProfiles("test")
class CreditNoteServiceTest extends AbstractIntegrationTest {
    @Autowired
    private CreditNoteService creditNoteService;

    @Autowired
    private com.red_escuela.backend.billing.usecase.creditnote.ListCreditNotesForStudentUseCase listCreditNotesForStudentUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.creditnote.CreateManualCreditNoteUseCase createManualCreditNoteUseCase;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private com.red_escuela.backend.academicstructure.repository.InstitutionRepository institutionRepository;

    @Autowired
    private StudentRepository studentRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(Person person) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(person.getId().toString(), null));
    }

    private Person persistPerson(String username) {
        Address address = addressRepository.save(new Address());
        Person person = new Person();
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        return personRepository.save(person);
    }

    private Student persistStudent(String dni) {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba " + System.nanoTime());
        institution = institutionRepository.save(institution);

        Student student = new Student();
        student.setPerson(persistPerson(dni));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        return studentRepository.save(student);
    }

    @Test
    void listForStudentWithNonExistentStudentThrows404() {
        assertThatThrownBy(() -> listCreditNotesForStudentUseCase.list(999999L))
                .isInstanceOf(com.red_escuela.backend.identity.exception.StudentNotFoundException.class);
    }

    @Test
    void createManualWithAdministrativeReasonIsAccepted() {
        Person admin = persistPerson("admin.credit1." + System.nanoTime());
        Student student = persistStudent("99100001." + System.nanoTime());
        authenticateAs(admin);

        CreditNote creditNote = createManualCreditNoteUseCase.create(student.getId(),
                new CreateCreditNoteRequest(ars(500), CreditNoteReasonType.ADMINISTRATIVE_CORRECTION, "Corrección de cobro duplicado"));

        assertThat(creditNote.getId()).isNotNull();
        assertThat(creditNoteService.availableBalance(student.getId(), Currency.ARS)).isEqualTo(ars(500));
    }

    @Test
    void createManualWithSystemReasonIsRejected() {
        Person admin = persistPerson("admin.credit2." + System.nanoTime());
        Student student = persistStudent("99100002." + System.nanoTime());
        authenticateAs(admin);

        assertThatThrownBy(() -> createManualCreditNoteUseCase.create(student.getId(),
                new CreateCreditNoteRequest(ars(500), CreditNoteReasonType.EARLY_WITHDRAWAL_UPFRONT_UNUSED, "Intento inválido")))
                .isInstanceOf(InvalidCreditNoteReasonException.class);
    }

    @Test
    void twoConcurrentConsumptionsWithinTheTotalBalanceBothSucceed() throws Exception {
        Person admin = persistPerson("admin.credit3." + System.nanoTime());
        Student student = persistStudent("99100003." + System.nanoTime());
        creditNoteService.createFor(student, ars(600), CreditNoteReasonType.ADMINISTRATIVE_CORRECTION, "Nota 1", admin);
        creditNoteService.createFor(student, ars(600), CreditNoteReasonType.ADMINISTRATIVE_CORRECTION, "Nota 2", admin);

        Invoice invoiceA = persistInvoice(student, ars(600));
        Invoice invoiceB = persistInvoice(student, ars(600));
        Payment paymentA = persistPayment(admin);
        Payment paymentB = persistPayment(admin);

        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch arranca = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Object> consumoA = () -> {
            listos.countDown();
            arranca.await();
            return creditNoteService.consumeForInvoice(student.getId(), ars(600), invoiceA, paymentA);
        };
        Callable<Object> consumoB = () -> {
            listos.countDown();
            arranca.await();
            return creditNoteService.consumeForInvoice(student.getId(), ars(600), invoiceB, paymentB);
        };

        Future<Object> resultadoA = executor.submit(consumoA);
        Future<Object> resultadoB = executor.submit(consumoB);
        listos.await(5, TimeUnit.SECONDS);
        arranca.countDown();

        int exitos = 0;
        for (Future<Object> resultado : List.of(resultadoA, resultadoB)) {
            try {
                resultado.get(30, TimeUnit.SECONDS);
                exitos++;
            } catch (ExecutionException ignored) {
            }
        }
        executor.shutdown();

        assertThat(exitos).isEqualTo(2);
        assertThat(creditNoteService.availableBalance(student.getId(), Currency.ARS)).isEqualTo(ars(0));
    }

    @Test
    void consumptionWithoutSufficientBalanceIsRejected() {
        Person admin = persistPerson("admin.credit4." + System.nanoTime());
        Student student = persistStudent("99100004." + System.nanoTime());
        creditNoteService.createFor(student, ars(300), CreditNoteReasonType.ADMINISTRATIVE_CORRECTION, "Nota", admin);
        Invoice invoice = persistInvoice(student, ars(1000));
        Payment payment = persistPayment(admin);

        assertThatThrownBy(() -> creditNoteService.consumeForInvoice(student.getId(), ars(1000), invoice, payment))
                .isInstanceOf(InsufficientCreditBalanceException.class);
    }

    private Invoice persistInvoice(Student student, Money amount) {
        Invoice invoice = new Invoice();
        invoice.setFeeType(FeeType.MONTHLY_INSTALLMENT);
        invoice.setPeriod(YearMonth.now());
        invoice.setDueDate(LocalDate.now());
        invoice.setBaseAmount(amount);
        invoice.setStatus(InvoiceStatus.PENDING);
        return invoiceRepository.save(invoice);
    }

    private Payment persistPayment(Person registeredBy) {
        Payment payment = new Payment();
        payment.setAmount(ars(600));
        payment.setMethod(PaymentMethod.CREDIT_BALANCE);
        payment.setStatus(PaymentStatus.CONFIRMED);
        payment.setRegisteredBy(registeredBy);
        payment.setPaidAt(LocalDateTime.now());
        return paymentRepository.save(payment);
    }
}
