package com.red_escuela.backend.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.dto.CreateInvoiceRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.entity.Invoice;
import com.red_escuela.backend.entity.InvoiceStatus;
import com.red_escuela.backend.entity.Payment;
import com.red_escuela.backend.entity.PaymentReceipt;
import com.red_escuela.backend.entity.PaymentReceiptStatus;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.exception.InvalidPaymentReceiptStateException;
import com.red_escuela.backend.exception.PaymentReceiptAlreadyPendingException;
import com.red_escuela.backend.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprobante subido por el propio Alumno — paso previo sin confirmar, nunca un
 * Payment real hasta que se aprueba (ver PaymentReceipt). Reintentos ilimitados
 * tras un rechazo (decisión de negocio).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentReceiptServiceTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentReceiptService paymentReceiptService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private PaymentRepository paymentRepository;

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

    private MultipartFile comprobante() {
        return new MockMultipartFile("file", "comprobante.pdf", "application/pdf", "contenido de prueba".getBytes());
    }

    @Test
    void subirUnComprobanteLoDejaPendienteDeRevision() {
        Student student = persistStudent("79920001");
        entityManager.flush();
        Invoice invoice = invoiceService.crear(student.getId(), new CreateInvoiceRequest("Matrícula", BigDecimal.valueOf(50000), LocalDate.now().plusDays(10)));

        PaymentReceipt receipt = paymentReceiptService.subir(invoice.getId(), comprobante());

        assertThat(receipt.getStatus()).isEqualTo(PaymentReceiptStatus.PENDING_REVIEW);
        assertThat(receipt.getFilePath()).isNotBlank();
    }

    @Test
    void noSePuedeSubirUnSegundoComprobanteMientrasElPrimeroSigueEnRevision() {
        Student student = persistStudent("79920002");
        entityManager.flush();
        Invoice invoice = invoiceService.crear(student.getId(), new CreateInvoiceRequest("Matrícula", BigDecimal.valueOf(50000), LocalDate.now().plusDays(10)));
        paymentReceiptService.subir(invoice.getId(), comprobante());

        assertThatThrownBy(() -> paymentReceiptService.subir(invoice.getId(), comprobante()))
                .isInstanceOf(PaymentReceiptAlreadyPendingException.class);
    }

    @Test
    void aprobarUnComprobanteCreaElPaymentRealYMarcaLaFacturaComoPagada() {
        Student student = persistStudent("79920003");
        Person administrativo = persistPersonBase("ADMIN" + System.nanoTime());
        entityManager.flush();
        Invoice invoice = invoiceService.crear(student.getId(), new CreateInvoiceRequest("Matrícula", BigDecimal.valueOf(50000), LocalDate.now().plusDays(10)));
        PaymentReceipt receipt = paymentReceiptService.subir(invoice.getId(), comprobante());

        paymentReceiptService.aprobar(receipt.getId(), administrativo);

        PaymentReceipt aprobado = entityManager.find(PaymentReceipt.class, receipt.getId());
        assertThat(aprobado.getStatus()).isEqualTo(PaymentReceiptStatus.APPROVED);
        assertThat(aprobado.getReviewedBy().getId()).isEqualTo(administrativo.getId());

        Invoice pagada = entityManager.find(Invoice.class, invoice.getId());
        assertThat(pagada.getStatus()).isEqualTo(InvoiceStatus.PAID);

        List<Payment> pagos = paymentRepository.findByInvoiceId(invoice.getId());
        assertThat(pagos).hasSize(1);
        assertThat(pagos.get(0).getAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        assertThat(pagos.get(0).getRegisteredBy().getId()).isEqualTo(administrativo.getId());
    }

    @Test
    void rechazarUnComprobanteNoCreaUnPaymentYPermiteReintentarSinLimite() {
        Student student = persistStudent("79920004");
        Person administrativo = persistPersonBase("ADMIN" + System.nanoTime());
        entityManager.flush();
        Invoice invoice = invoiceService.crear(student.getId(), new CreateInvoiceRequest("Matrícula", BigDecimal.valueOf(50000), LocalDate.now().plusDays(10)));
        PaymentReceipt primerIntento = paymentReceiptService.subir(invoice.getId(), comprobante());

        paymentReceiptService.rechazar(primerIntento.getId(), administrativo, "Comprobante ilegible");

        PaymentReceipt rechazado = entityManager.find(PaymentReceipt.class, primerIntento.getId());
        assertThat(rechazado.getStatus()).isEqualTo(PaymentReceiptStatus.REJECTED);
        assertThat(rechazado.getRejectionReason()).isEqualTo("Comprobante ilegible");
        assertThat(paymentRepository.findByInvoiceId(invoice.getId())).isEmpty();

        // Reintento sin límite: el primero ya no está PENDING_REVIEW, así que uno nuevo es válido.
        PaymentReceipt segundoIntento = paymentReceiptService.subir(invoice.getId(), comprobante());
        assertThat(segundoIntento.getStatus()).isEqualTo(PaymentReceiptStatus.PENDING_REVIEW);
    }

    @Test
    void aprobarUnComprobanteYaDecididoFalla() {
        Student student = persistStudent("79920005");
        Person administrativo = persistPersonBase("ADMIN" + System.nanoTime());
        entityManager.flush();
        Invoice invoice = invoiceService.crear(student.getId(), new CreateInvoiceRequest("Matrícula", BigDecimal.valueOf(50000), LocalDate.now().plusDays(10)));
        PaymentReceipt receipt = paymentReceiptService.subir(invoice.getId(), comprobante());
        paymentReceiptService.aprobar(receipt.getId(), administrativo);

        assertThatThrownBy(() -> paymentReceiptService.aprobar(receipt.getId(), administrativo))
                .isInstanceOf(InvalidPaymentReceiptStateException.class);
    }
}
