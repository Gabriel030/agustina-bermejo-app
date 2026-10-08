package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.dto.CreateAdHocInvoiceRequest;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.exception.InvalidAdHocFeeTypeException;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
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
import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@ActiveProfiles("test")
class AdHocInvoiceServiceTest extends AbstractIntegrationTest {
    @Autowired
    private com.red_escuela.backend.billing.usecase.invoice.CreateAdHocInvoiceForCourseEnrollmentUseCase createAdHocInvoiceForCourseEnrollmentUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.invoice.CreateAdHocInvoiceForProgramEnrollmentUseCase createAdHocInvoiceForProgramEnrollmentUseCase;

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
    void administrativeChargesExamFeeWithoutAffectingTheAnnualTemplate() {
        ProgramEnrollment enrollment = persistProgramEnrollment("99200001." + System.nanoTime());
        CreateAdHocInvoiceRequest request = new CreateAdHocInvoiceRequest(
                FeeType.EXAM_FEE, ars(1500), LocalDate.now(), "Derecho de examen final", null, null);

        Invoice created = createAdHocInvoiceForProgramEnrollmentUseCase.create(enrollment.getId(), request).invoice();

        assertThat(created.getId()).isNotNull();
        assertThat(created.getFeeType()).isEqualTo(FeeType.EXAM_FEE);
        assertThat(created.findProgramEnrollmentId()).contains(enrollment.getId());

        List<Invoice> invoices = invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId());
        assertThat(invoices).hasSize(1);
        long recurrentes = invoices.stream().filter(i -> i.getFeeType() == FeeType.MONTHLY_INSTALLMENT || i.getFeeType() == FeeType.ENROLLMENT_FEE).count();
        assertThat(recurrentes).isZero();
    }

    @Test
    void twoExamFeesInTheSameMonthDoNotClashWithTheUniqueConstraint() {
        CourseEnrollment enrollment = persistCourseEnrollment("99200002." + System.nanoTime());
        LocalDate dueDate = LocalDate.now();
        CreateAdHocInvoiceRequest primero = new CreateAdHocInvoiceRequest(
                FeeType.EXAM_FEE, ars(1000), dueDate, "Derecho de examen — Matemática", null, null);
        CreateAdHocInvoiceRequest segundo = new CreateAdHocInvoiceRequest(
                FeeType.EXAM_FEE, ars(1000), dueDate, "Derecho de examen — Física", null, null);

        Invoice invoiceUno = createAdHocInvoiceForCourseEnrollmentUseCase.create(enrollment.getId(), primero).invoice();
        Invoice invoiceDos = createAdHocInvoiceForCourseEnrollmentUseCase.create(enrollment.getId(), segundo).invoice();

        assertThat(invoiceUno.getId()).isNotEqualTo(invoiceDos.getId());
        assertThat(invoiceUno.getPeriod()).isEqualTo(YearMonth.from(dueDate));
        assertThat(invoiceDos.getPeriod()).isEqualTo(YearMonth.from(dueDate));
    }

    @Test
    void recurringFeeTypeIsRejectedForAdHocCreation() {
        CourseEnrollment enrollment = persistCourseEnrollment("99200003." + System.nanoTime());
        CreateAdHocInvoiceRequest request = new CreateAdHocInvoiceRequest(
                FeeType.MONTHLY_INSTALLMENT, ars(500), LocalDate.now(), "Intento inválido", null, null);

        assertThatThrownBy(() -> createAdHocInvoiceForCourseEnrollmentUseCase.create(enrollment.getId(), request))
                .isInstanceOf(InvalidAdHocFeeTypeException.class);
    }

    @Test
    void adHocCreationSavesSubjectAndDescription() {
        CourseEnrollment enrollment = persistCourseEnrollment("99200004." + System.nanoTime());
        CreateAdHocInvoiceRequest request = new CreateAdHocInvoiceRequest(
                FeeType.RETAKEN_SUBJECT_FEE, ars(800), LocalDate.now(), "Recursada informal", null, null);

        Invoice created = createAdHocInvoiceForCourseEnrollmentUseCase.create(enrollment.getId(), request).invoice();

        assertThat(created.getPrimaryLine().getConcept()).isEqualTo("Recursada informal");
        assertThat(created.getPrimaryLine().getSubjectId()).isNull();
        assertThat(created.getPrimaryLine().getExamBoardRegistrationId()).isNull();
    }
}
