package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.billing.dto.CreateCreditNoteRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.billing.entity.CreditNote;
import com.red_escuela.backend.billing.entity.CreditNoteReasonType;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.Payment;
import com.red_escuela.backend.billing.entity.PaymentMethod;
import com.red_escuela.backend.billing.entity.PaymentStatus;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.billing.exception.InsufficientCreditBalanceException;
import com.red_escuela.backend.billing.exception.InvalidCreditNoteReasonException;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PaymentRepository;
import com.red_escuela.backend.repository.PersonRepository;
import com.red_escuela.backend.repository.StudentRepository;
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

// Fase 6.6: test 130 (alta manual de CreditNote, razón válida vs. rechazada) y test 106
// (consumo concurrente de CreditNote no debe fallar espuriamente si el pool alcanza).
@SpringBootTest
@ActiveProfiles("test")
class CreditNoteServiceTest extends AbstractIntegrationTest {

    @Autowired
    private CreditNoteService creditNoteService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private com.red_escuela.backend.repository.InstitutionRepository institutionRepository;

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

    // Hallazgo de QA manual (2026-09-08): listForStudent devolvía [] para un student_id
    // inexistente en vez de 404 — inconsistente con createManual, que sí valida.
    @Test
    void listForStudentConStudentInexistenteLanza404() {
        assertThatThrownBy(() -> creditNoteService.listForStudent(999999L))
                .isInstanceOf(com.red_escuela.backend.exception.StudentNotFoundException.class);
    }

    @Test
    void createManualConRazonAdministrativaEsAceptada() {
        Person admin = persistPerson("admin.credit1." + System.nanoTime());
        Student student = persistStudent("99100001." + System.nanoTime());
        authenticateAs(admin);

        CreditNote creditNote = creditNoteService.createManual(student.getId(),
                new CreateCreditNoteRequest(BigDecimal.valueOf(500), CreditNoteReasonType.ADMINISTRATIVE_CORRECTION, "Corrección de cobro duplicado"));

        assertThat(creditNote.getId()).isNotNull();
        assertThat(creditNoteService.saldoDisponible(student.getId())).isEqualByComparingTo(BigDecimal.valueOf(500));
    }

    @Test
    void createManualConRazonDeSistemaEsRechazada() {
        Person admin = persistPerson("admin.credit2." + System.nanoTime());
        Student student = persistStudent("99100002." + System.nanoTime());
        authenticateAs(admin);

        assertThatThrownBy(() -> creditNoteService.createManual(student.getId(),
                new CreateCreditNoteRequest(BigDecimal.valueOf(500), CreditNoteReasonType.EARLY_WITHDRAWAL_UPFRONT_UNUSED, "Intento inválido")))
                .isInstanceOf(InvalidCreditNoteReasonException.class);
    }

    // Regresión de auditoría 2026-09-07: dos consumos concurrentes del mismo alumno, cada uno
    // dentro del saldo total disponible, deben tener AMBOS éxito — antes del fix, uno de los dos
    // fallaba con ObjectOptimisticLockingFailureException por una CreditNote cacheada sin lock en
    // el persistence context de su propia transacción.
    @Test
    void dosConsumosConcurrentesDentroDelSaldoTotalTienenAmbosExito() throws Exception {
        Person admin = persistPerson("admin.credit3." + System.nanoTime());
        Student student = persistStudent("99100003." + System.nanoTime());
        creditNoteService.createFor(student, BigDecimal.valueOf(600), CreditNoteReasonType.ADMINISTRATIVE_CORRECTION, "Nota 1", admin);
        creditNoteService.createFor(student, BigDecimal.valueOf(600), CreditNoteReasonType.ADMINISTRATIVE_CORRECTION, "Nota 2", admin);

        Invoice invoiceA = persistInvoice(student, BigDecimal.valueOf(600));
        Invoice invoiceB = persistInvoice(student, BigDecimal.valueOf(600));
        Payment paymentA = persistPayment(admin);
        Payment paymentB = persistPayment(admin);

        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch arranca = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Object> consumoA = () -> {
            listos.countDown();
            arranca.await();
            return creditNoteService.consumeForInvoice(student.getId(), BigDecimal.valueOf(600), invoiceA, paymentA);
        };
        Callable<Object> consumoB = () -> {
            listos.countDown();
            arranca.await();
            return creditNoteService.consumeForInvoice(student.getId(), BigDecimal.valueOf(600), invoiceB, paymentB);
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
                // Se cuentan solo los éxitos: con $1200 de pool para dos consumos de $600 no debería
                // haber ningún fallo.
            }
        }
        executor.shutdown();

        assertThat(exitos).isEqualTo(2);
        assertThat(creditNoteService.saldoDisponible(student.getId())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void consumoSinSaldoSuficienteEsRechazado() {
        Person admin = persistPerson("admin.credit4." + System.nanoTime());
        Student student = persistStudent("99100004." + System.nanoTime());
        creditNoteService.createFor(student, BigDecimal.valueOf(300), CreditNoteReasonType.ADMINISTRATIVE_CORRECTION, "Nota", admin);
        Invoice invoice = persistInvoice(student, BigDecimal.valueOf(1000));
        Payment payment = persistPayment(admin);

        assertThatThrownBy(() -> creditNoteService.consumeForInvoice(student.getId(), BigDecimal.valueOf(1000), invoice, payment))
                .isInstanceOf(InsufficientCreditBalanceException.class);
    }

    // No hace falta un CourseEnrollment/ProgramEnrollment real: consumeForInvoice solo lee
    // amountDue/status de la Invoice, y el studentId lo pasa el propio test explícitamente
    // (chk_invoice_billable permite ambos en null, ver V43__billing_nucleo_facturacion.sql).
    private Invoice persistInvoice(Student student, BigDecimal amount) {
        Invoice invoice = new Invoice();
        invoice.setFeeType(FeeType.RECURRING_FEE);
        invoice.setPeriod(YearMonth.now());
        invoice.setDueDate(LocalDate.now());
        invoice.setBaseAmount(amount);
        invoice.setStatus(InvoiceStatus.PENDING);
        return invoiceRepository.save(invoice);
    }

    private Payment persistPayment(Person registeredBy) {
        Payment payment = new Payment();
        payment.setAmount(BigDecimal.valueOf(600));
        payment.setMethod(PaymentMethod.CREDIT_BALANCE);
        payment.setStatus(PaymentStatus.CONFIRMED);
        payment.setRegisteredBy(registeredBy);
        payment.setPaidAt(LocalDateTime.now());
        return paymentRepository.save(payment);
    }
}
