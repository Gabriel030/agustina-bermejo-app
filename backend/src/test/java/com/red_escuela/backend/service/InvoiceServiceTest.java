package com.red_escuela.backend.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.dto.CreateInvoiceRequest;
import com.red_escuela.backend.dto.DebtSummaryResponse;
import com.red_escuela.backend.dto.RegisterPaymentRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.entity.Invoice;
import com.red_escuela.backend.entity.InvoiceStatus;
import com.red_escuela.backend.entity.PaymentMethod;
import com.red_escuela.backend.entity.PaymentStanding;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.exception.InvoiceAlreadyCancelledException;
import com.red_escuela.backend.exception.PaymentExceedsInvoiceBalanceException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RF-AL-09/RF-AD-08/RF-AL-10: facturación interna, recálculo automático de Invoice.status al
 * register un pago (decisión 4), cálculo on-demand de deuda (decisión 3) y de PaymentStanding
 * (decisión 17, sin ningún caller de bloqueo — se verifica explícitamente que no rompe la
 * inscripción existente).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InvoiceServiceTest extends AbstractIntegrationTest {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private EntityManager entityManager;

    @AfterEach
    void clearContextOfSecurity() {
        SecurityContextHolder.clearContext();
    }

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

    private void autenticarComoAdministrativo() {
        Person admin = persistPersonBase("ADMIN" + System.nanoTime());
        entityManager.flush();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(admin.getId().toString(), null));
    }

    @Test
    void registerTwoPartialPaymentsUntilCompleteTheAmountGoesFromPendingToPartiallyPaidToPaid() {
        Student student = persistStudent("79900001");
        entityManager.flush();
        Invoice invoice = invoiceService.create(student.getId(), new CreateInvoiceRequest("Cuota 1", BigDecimal.valueOf(1000), LocalDate.now().plusDays(30)));
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PENDING);

        autenticarComoAdministrativo();
        paymentService.registerPayment(invoice.getId(), new RegisterPaymentRequest(BigDecimal.valueOf(400), PaymentMethod.CASH, null));
        Invoice trasPrimerPago = entityManager.find(Invoice.class, invoice.getId());
        assertThat(trasPrimerPago.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);

        paymentService.registerPayment(invoice.getId(), new RegisterPaymentRequest(BigDecimal.valueOf(600), PaymentMethod.TRANSFER, null));
        Invoice trasSegundoPago = entityManager.find(Invoice.class, invoice.getId());
        assertThat(trasSegundoPago.getStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void registerAPaymentThatExceedsTheBalancePendingFailsWithoutCreateThePayment() {
        Student student = persistStudent("79900002");
        entityManager.flush();
        Invoice invoice = invoiceService.create(student.getId(), new CreateInvoiceRequest("Cuota 1", BigDecimal.valueOf(500), LocalDate.now().plusDays(30)));

        autenticarComoAdministrativo();
        assertThatThrownBy(() -> paymentService.registerPayment(
                invoice.getId(), new RegisterPaymentRequest(BigDecimal.valueOf(600), PaymentMethod.CASH, null)))
                .isInstanceOf(PaymentExceedsInvoiceBalanceException.class);

        Invoice sinCambios = entityManager.find(Invoice.class, invoice.getId());
        assertThat(sinCambios.getStatus()).isEqualTo(InvoiceStatus.PENDING);
    }

    @Test
    void cancelAInvoiceAlreadyPaidFails() {
        Student student = persistStudent("79900010");
        entityManager.flush();
        Invoice pagada = invoiceService.create(student.getId(), new CreateInvoiceRequest("Pagada", BigDecimal.valueOf(100), LocalDate.now().plusDays(30)));
        autenticarComoAdministrativo();
        paymentService.registerPayment(pagada.getId(), new RegisterPaymentRequest(BigDecimal.valueOf(100), PaymentMethod.CASH, null));

        assertThatThrownBy(() -> invoiceService.cancel(pagada.getId()))
                .isInstanceOf(InvoiceAlreadyCancelledException.class);
    }

    @Test
    void cancelAInvoiceAlreadyCancelledFails() {
        Student student = persistStudent("79900003");
        entityManager.flush();
        Invoice invoice = invoiceService.create(student.getId(), new CreateInvoiceRequest("Cuota", BigDecimal.valueOf(100), LocalDate.now().plusDays(30)));
        invoiceService.cancel(invoice.getId());

        assertThatThrownBy(() -> invoiceService.cancel(invoice.getId()))
                .isInstanceOf(InvoiceAlreadyCancelledException.class);
    }

    @Test
    void cancelAInvoicePendingTheGoesToCancelled() {
        Student student = persistStudent("79900004");
        entityManager.flush();
        Invoice invoice = invoiceService.create(student.getId(), new CreateInvoiceRequest("Pendiente", BigDecimal.valueOf(100), LocalDate.now().plusDays(30)));

        invoiceService.cancel(invoice.getId());
        Invoice cancelada = entityManager.find(Invoice.class, invoice.getId());
        assertThat(cancelada.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
    }

    @Test
    void calculateDebtOfAStudentWithoutInvoicesGivesWithoutDebt() {
        Student student = persistStudent("79900005");
        entityManager.flush();
        DebtSummaryResponse resumen = invoiceService.calculateDebt(student.getId());
        assertThat(resumen.tieneDeuda()).isFalse();
        assertThat(resumen.montoAdeudado()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateDebtWithAInvoicePendingAndAPaymentPartialGivesTheBalanceRemaining() {
        Student student = persistStudent("79900006");
        entityManager.flush();
        Invoice invoice = invoiceService.create(student.getId(), new CreateInvoiceRequest("Cuota", BigDecimal.valueOf(1000), LocalDate.now().plusDays(30)));
        autenticarComoAdministrativo();
        paymentService.registerPayment(invoice.getId(), new RegisterPaymentRequest(BigDecimal.valueOf(400), PaymentMethod.CASH, null));

        DebtSummaryResponse resumen = invoiceService.calculateDebt(student.getId());
        assertThat(resumen.tieneDeuda()).isTrue();
        assertThat(resumen.montoAdeudado()).isEqualByComparingTo(BigDecimal.valueOf(600));
    }

    @Test
    void calculateStatusOfPaymentWithoutInvoicesGivesWithoutInformation() {
        Student student = persistStudent("79900007");
        entityManager.flush();
        assertThat(invoiceService.calculatePaymentStatus(student.getId())).isEqualTo(PaymentStanding.SIN_INFORMACION);
    }

    @Test
    void calculateStatusOfPaymentWithInvoiceNotOverdueGivesCurrent() {
        Student student = persistStudent("79900008");
        entityManager.flush();
        invoiceService.create(student.getId(), new CreateInvoiceRequest("Cuota futura", BigDecimal.valueOf(100), LocalDate.now().plusDays(10)));
        assertThat(invoiceService.calculatePaymentStatus(student.getId())).isEqualTo(PaymentStanding.AL_DIA);
    }

    @Test
    void calculateStatusOfPaymentWithInvoiceOverdueWithoutPayGivesInArrearsAndPayingItGoesBackToCurrentImmediately() {
        Student student = persistStudent("79900009");
        entityManager.flush();
        Invoice invoice = invoiceService.create(student.getId(), new CreateInvoiceRequest("Cuota vencida", BigDecimal.valueOf(100), LocalDate.now().minusDays(5)));
        assertThat(invoiceService.calculatePaymentStatus(student.getId())).isEqualTo(PaymentStanding.MOROSO);

        autenticarComoAdministrativo();
        paymentService.registerPayment(invoice.getId(), new RegisterPaymentRequest(BigDecimal.valueOf(100), PaymentMethod.CASH, null));
        assertThat(invoiceService.calculatePaymentStatus(student.getId())).isEqualTo(PaymentStanding.AL_DIA);
    }
}
