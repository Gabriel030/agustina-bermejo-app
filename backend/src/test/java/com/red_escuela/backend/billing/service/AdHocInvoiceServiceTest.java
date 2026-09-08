package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.dto.CreateAdHocInvoiceRequest;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.exception.InvalidAdHocFeeTypeException;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.repository.CourseRepository;
import com.red_escuela.backend.repository.InstitutionRepository;
import com.red_escuela.backend.repository.PersonRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.repository.ProgramRepository;
import com.red_escuela.backend.repository.StudentRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Fase 6.7: tests 76, 77, 78 (cobro de derecho de examen sin afectar la plantilla anual, dos
// conceptos ad-hoc del mismo tipo en el mismo mes sin chocar con el unique constraint, y alta
// ad-hoc restringida a ADMINISTRATIVO se cubre en el controller — acá se testea solo el service).
@SpringBootTest
@ActiveProfiles("test")
class AdHocInvoiceServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AdHocInvoiceService adHocInvoiceService;

    @Autowired
    private InvoiceRepository invoiceRepository;

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

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private ProgramEnrollmentRepository programEnrollmentRepository;

    private Person persistPerson(String username) {
        Address address = addressRepository.save(new Address());
        Person person = new Person();
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        return personRepository.save(person);
    }

    private CourseEnrollment persistCourseEnrollment(String dni) {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba " + System.nanoTime());
        institution = institutionRepository.save(institution);

        Student student = new Student();
        student.setPerson(persistPerson(dni));
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
        return courseEnrollmentRepository.save(enrollment);
    }

    private ProgramEnrollment persistProgramEnrollment(String dni) {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba " + System.nanoTime());
        institution = institutionRepository.save(institution);

        Student student = new Student();
        student.setPerson(persistPerson(dni));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        student = studentRepository.save(student);

        Program program = new Program();
        program.setName("Carrera de prueba " + System.nanoTime());
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        program = programRepository.save(program);

        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        return programEnrollmentRepository.save(enrollment);
    }

    @Test
    void administrativoCobraDerechoDeExamenSinAfectarLaPlantillaAnual() {
        ProgramEnrollment enrollment = persistProgramEnrollment("99200001." + System.nanoTime());
        CreateAdHocInvoiceRequest request = new CreateAdHocInvoiceRequest(
                FeeType.EXAM_RIGHT, BigDecimal.valueOf(1500), LocalDate.now(), "Derecho de examen final", null, null);

        Invoice created = adHocInvoiceService.createForProgramEnrollment(enrollment.getId(), request);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getFeeType()).isEqualTo(FeeType.EXAM_RIGHT);
        assertThat(created.getProgramEnrollment().getId()).isEqualTo(enrollment.getId());

        List<Invoice> invoices = invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId());
        assertThat(invoices).hasSize(1);
        long recurrentes = invoices.stream().filter(i -> i.getFeeType() == FeeType.RECURRING_FEE || i.getFeeType() == FeeType.ENROLLMENT_FEE).count();
        assertThat(recurrentes).isZero();
    }

    @Test
    void dosDerechosDeExamenEnElMismoMesNoChocanConElUniqueConstraint() {
        CourseEnrollment enrollment = persistCourseEnrollment("99200002." + System.nanoTime());
        LocalDate dueDate = LocalDate.now();
        CreateAdHocInvoiceRequest primero = new CreateAdHocInvoiceRequest(
                FeeType.EXAM_RIGHT, BigDecimal.valueOf(1000), dueDate, "Derecho de examen — Matemática", null, null);
        CreateAdHocInvoiceRequest segundo = new CreateAdHocInvoiceRequest(
                FeeType.EXAM_RIGHT, BigDecimal.valueOf(1000), dueDate, "Derecho de examen — Física", null, null);

        Invoice invoiceUno = adHocInvoiceService.createForCourseEnrollment(enrollment.getId(), primero);
        Invoice invoiceDos = adHocInvoiceService.createForCourseEnrollment(enrollment.getId(), segundo);

        assertThat(invoiceUno.getId()).isNotEqualTo(invoiceDos.getId());
        assertThat(invoiceUno.getPeriod()).isEqualTo(YearMonth.from(dueDate));
        assertThat(invoiceDos.getPeriod()).isEqualTo(YearMonth.from(dueDate));
    }

    @Test
    void feeTypeRecurrenteEsRechazadoParaAltaAdHoc() {
        CourseEnrollment enrollment = persistCourseEnrollment("99200003." + System.nanoTime());
        CreateAdHocInvoiceRequest request = new CreateAdHocInvoiceRequest(
                FeeType.RECURRING_FEE, BigDecimal.valueOf(500), LocalDate.now(), "Intento inválido", null, null);

        assertThatThrownBy(() -> adHocInvoiceService.createForCourseEnrollment(enrollment.getId(), request))
                .isInstanceOf(InvalidAdHocFeeTypeException.class);
    }

    @Test
    void altaAdHocGuardaSubjectYDescripcion() {
        CourseEnrollment enrollment = persistCourseEnrollment("99200004." + System.nanoTime());
        CreateAdHocInvoiceRequest request = new CreateAdHocInvoiceRequest(
                FeeType.RETAKEN_SUBJECT_FEE, BigDecimal.valueOf(800), LocalDate.now(), "Recursada informal", null, null);

        Invoice created = adHocInvoiceService.createForCourseEnrollment(enrollment.getId(), request);

        assertThat(created.getDescription()).isEqualTo("Recursada informal");
        assertThat(created.getSubject()).isNull();
        assertThat(created.getExamBoardRegistration()).isNull();
    }
}
