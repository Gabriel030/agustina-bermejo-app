package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.dto.RegisterPaymentRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.Payment;
import com.red_escuela.backend.billing.entity.PaymentAllocation;
import com.red_escuela.backend.billing.entity.PaymentMethod;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.billing.exception.PaymentExceedsInvoiceBalanceException;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.repository.CourseRepository;
import com.red_escuela.backend.repository.InstitutionRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PaymentAllocationRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.repository.TermRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

/**
 * Fase 6.2 (Pagos y allocations): tests 18 y 83 de la spec, con hilos reales — el resto de la
 * fase (PaymentServiceTest) prueba la misma validación de forma secuencial, que no ejercita el
 * lock pesimista. Deliberadamente SIN @Transactional a nivel de clase, mismo motivo que
 * PaymentServiceTest: findByIdForUpdate necesita su propia transacción real por request.
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private com.red_escuela.backend.billing.usecase.payment.RegisterPaymentUseCase registerPaymentUseCase;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PaymentAllocationRepository paymentAllocationRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @Autowired
    private TermRepository termRepository;

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

    private CourseEnrollment persistCourseEnrollment(Student student) {
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
        return courseEnrollmentRepository.save(enrollment);
    }

    private Invoice persistInvoice(CourseEnrollment enrollment, BigDecimal amount) {
        Invoice invoice = new Invoice();
        invoice.setCourseEnrollment(enrollment);
        invoice.setFeeType(FeeType.RECURRING_FEE);
        invoice.setPeriod(YearMonth.now());
        invoice.setDueDate(LocalDate.now());
        invoice.setBaseAmount(amount);
        invoice.setStatus(InvoiceStatus.PENDING);
        return invoiceRepository.save(invoice);
    }

    private Payment ejecutarPagoConcurrente(Long invoiceId, BigDecimal monto, Person admin,
                                             CountDownLatch listos, CountDownLatch arranca) throws Exception {
        listos.countDown();
        arranca.await();
        return registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoiceId, monto)),
                PaymentMethod.CASH, false, null, null, null, null), admin);
    }

    @Test
    void dosAdministrativosRegistrandoPagoSobreLaMismaCuotaEnParaleloNoDuplicaElSaldo() throws Exception {
        Person admin1 = persistPerson("admin.concurrencia1." + System.nanoTime());
        Person admin2 = persistPerson("admin.concurrencia2." + System.nanoTime());
        Student student = persistStudent("98000001");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, BigDecimal.valueOf(10000));

        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch arranca = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Payment> intento1 = () -> ejecutarPagoConcurrente(invoice.getId(), BigDecimal.valueOf(10000), admin1, listos, arranca);
        Callable<Payment> intento2 = () -> ejecutarPagoConcurrente(invoice.getId(), BigDecimal.valueOf(10000), admin2, listos, arranca);

        Future<Payment> resultado1 = executor.submit(intento1);
        Future<Payment> resultado2 = executor.submit(intento2);
        listos.await(5, TimeUnit.SECONDS);
        arranca.countDown();

        int exitos = 0;
        int rechazosPorSaldo = 0;
        for (Future<Payment> resultado : List.of(resultado1, resultado2)) {
            try {
                resultado.get(30, TimeUnit.SECONDS);
                exitos++;
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(PaymentExceedsInvoiceBalanceException.class);
                rechazosPorSaldo++;
            }
        }
        executor.shutdown();

        assertThat(exitos).isEqualTo(1);
        assertThat(rechazosPorSaldo).isEqualTo(1);
        assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getStatus()).isEqualTo(InvoiceStatus.PAID);
        List<PaymentAllocation> allocations = paymentAllocationRepository.findByInvoiceId(invoice.getId());
        assertThat(allocations).hasSize(1);
        assertThat(allocations.get(0).getAmountApplied()).isEqualByComparingTo(BigDecimal.valueOf(10000));
    }

    @Test
    void dosPagosParcialesConcurrentesNoSobrepasanElSaldo() throws Exception {
        Person admin1 = persistPerson("admin.concurrencia3." + System.nanoTime());
        Person admin2 = persistPerson("admin.concurrencia4." + System.nanoTime());
        Student student = persistStudent("98000002");
        CourseEnrollment enrollment = persistCourseEnrollment(student);
        Invoice invoice = persistInvoice(enrollment, BigDecimal.valueOf(1000));

        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch arranca = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Payment> intento1 = () -> ejecutarPagoConcurrente(invoice.getId(), BigDecimal.valueOf(600), admin1, listos, arranca);
        Callable<Payment> intento2 = () -> ejecutarPagoConcurrente(invoice.getId(), BigDecimal.valueOf(600), admin2, listos, arranca);

        Future<Payment> resultado1 = executor.submit(intento1);
        Future<Payment> resultado2 = executor.submit(intento2);
        listos.await(5, TimeUnit.SECONDS);
        arranca.countDown();

        int exitos = 0;
        int rechazosPorSaldo = 0;
        for (Future<Payment> resultado : List.of(resultado1, resultado2)) {
            try {
                resultado.get(30, TimeUnit.SECONDS);
                exitos++;
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(PaymentExceedsInvoiceBalanceException.class);
                rechazosPorSaldo++;
            }
        }
        executor.shutdown();

        assertThat(exitos).isEqualTo(1);
        assertThat(rechazosPorSaldo).isEqualTo(1);
        assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getStatus()).isEqualTo(InvoiceStatus.PENDING);
        List<PaymentAllocation> allocations = paymentAllocationRepository.findByInvoiceId(invoice.getId());
        assertThat(allocations).hasSize(1);
        assertThat(allocations.get(0).getAmountApplied()).isEqualByComparingTo(BigDecimal.valueOf(600));
    }
}
