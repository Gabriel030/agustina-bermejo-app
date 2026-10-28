package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.support.PriceFixtures;
import com.red_escuela.backend.billing.exception.DuplicateChargeException;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.note.domain.SystemCancellationOrigin;
import com.red_escuela.backend.billing.note.usecase.CancelInvoiceBySystemUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollmentApplication;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Tag("D-242")
@Tag("D-245")
class CourseAdmissionChargeReleaseIT extends AbstractIntegrationTest {
    @Autowired private CourseEnrollmentApplicationService applicationService;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private CancelInvoiceBySystemUseCase systemCancellation;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

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

    private Student persistStudent() {
        return transactionTemplate.execute(status -> {
            Institution institution = new Institution();
            institution.setName("Instituto " + System.nanoTime());
            entityManager.persist(institution);
            Student student = new Student();
            student.setPerson(persistPerson("7" + System.nanoTime()));
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            return student;
        });
    }

    private Course persistPricedCourse() {
        return transactionTemplate.execute(status -> {
            Term term = new Term();
            term.setYear(2026);
            term.setStatus(StatusType.ACTIVE);
            entityManager.persist(term);
            Course course = new Course();
            course.setName("Curso " + System.nanoTime());
            course.setTerm(term);
            course.setCapacity(20);
            course.setIsActive(StatusType.ACTIVE);
            entityManager.persist(course);
            PriceFixtures.persistPriceVersion(null, course.getId(), FeeType.MONTHLY_INSTALLMENT, "Precio de prueba", ars(BigDecimal.valueOf(15000)), LocalDate.now().minusDays(1), persistPerson("DIR" + System.nanoTime()).getId());
            return course;
        });
    }

    private String chargeKeyOf(Long invoiceId) {
        return jdbc.queryForObject("select charge_key from invoice_line where invoice_id = ?", String.class, invoiceId);
    }

    private MultipartFile receiptFile() {
        return new MockMultipartFile("comprobante", "comprobante.pdf", "application/pdf", "contenido de prueba".getBytes());
    }

    private CourseEnrollmentApplication request(Student student, Course course) {
        return applicationService.requestWithPayment(student.getId(), new CreateCourseEnrollmentRequest(course.getId(), null), receiptFile());
    }

    @Test
    @Tag("B2-22")
    void rejectedReceiptCancellationLetsTheStudentRequestTheSameCourseAgain() {
        Student student = persistStudent();
        Course course = persistPricedCourse();
        CourseEnrollmentApplication first = request(student, course);
        String key = chargeKeyOf(first.getInvoiceId());
        jdbc.update("update bank_transfer_receipt set status = 'REJECTED' where id = ?", first.getPaymentReceipt().getId());
        assertThatThrownBy(() -> request(student, course)).isInstanceOf(DuplicateChargeException.class);

        systemCancellation.cancelInvoiceWithoutDirector(first.getInvoiceId(), SystemCancellationOrigin.REJECTED_COURSE_RECEIPT, "Comprobante rechazado");
        CourseEnrollmentApplication second = request(student, course);

        assertThat(second.getInvoiceId()).isNotEqualTo(first.getInvoiceId());
        assertThat(chargeKeyOf(second.getInvoiceId())).isEqualTo(key);
        assertThat(invoiceRepository.findLiveInvoiceIdByChargeKey(key)).contains(second.getInvoiceId());
    }
}
