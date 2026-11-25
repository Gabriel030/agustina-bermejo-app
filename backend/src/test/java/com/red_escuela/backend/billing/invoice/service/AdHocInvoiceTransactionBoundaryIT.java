package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.billing.dto.CreateAdHocInvoiceRequest;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.usecase.invoice.CreateAdHocInvoiceForCourseEnrollmentUseCase;
import com.red_escuela.backend.billing.usecase.invoice.CreateAdHocInvoiceForProgramEnrollmentUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.AtomicityTestSupport;
import com.red_escuela.backend.testsupport.TermFixtures;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
class AdHocInvoiceTransactionBoundaryIT extends AbstractIntegrationTest {

    @Autowired
    private CreateAdHocInvoiceForCourseEnrollmentUseCase createForCourse;

    @Autowired
    private CreateAdHocInvoiceForProgramEnrollmentUseCase createForProgram;

    @Autowired
    private InvoiceViewLoader invoiceViewLoader;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TermRepository termRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private ProgramEnrollmentRepository programEnrollmentRepository;

    @Test
    void courseAdHocInvoiceFailingAfterIssuingLeavesNoInvoiceBehind() {
        CourseEnrollment enrollment = persistCourseEnrollment();
        CreateAdHocInvoiceRequest request = request("Derecho de examen curso");
        AtomicityTestSupport support = AtomicityTestSupport.observing(jdbcTemplate, "invoice", "invoice_line");

        support.assertNothingPersistedWhenFailingAtStep(1, steps -> {
            failWhenLoadingTheInvoiceView(steps);
            createForCourse.create(enrollment.getId(), request);
        });
    }

    @Test
    void programAdHocInvoiceFailingAfterIssuingLeavesNoInvoiceBehind() {
        ProgramEnrollment enrollment = persistProgramEnrollment();
        CreateAdHocInvoiceRequest request = request("Derecho de examen carrera");
        AtomicityTestSupport support = AtomicityTestSupport.observing(jdbcTemplate, "invoice", "invoice_line");

        support.assertNothingPersistedWhenFailingAtStep(1, steps -> {
            failWhenLoadingTheInvoiceView(steps);
            createForProgram.create(enrollment.getId(), request);
        });
    }

    @Test
    void courseAdHocInvoiceWithoutFailuresPersistsTheInvoice() {
        CourseEnrollment enrollment = persistCourseEnrollment();
        long invoicesBefore = count("invoice");

        Invoice created = createForCourse.create(enrollment.getId(), request("Derecho de examen curso")).invoice();

        assertThat(created.getFeeType()).isEqualTo(FeeType.EXAM_FEE);
        assertThat(count("invoice")).isEqualTo(invoicesBefore + 1);
    }

    @Test
    void programAdHocInvoiceWithoutFailuresPersistsTheInvoice() {
        ProgramEnrollment enrollment = persistProgramEnrollment();
        long invoicesBefore = count("invoice");

        Invoice created = createForProgram.create(enrollment.getId(), request("Derecho de examen carrera")).invoice();

        assertThat(created.getFeeType()).isEqualTo(FeeType.EXAM_FEE);
        assertThat(count("invoice")).isEqualTo(invoicesBefore + 1);
    }

    private void failWhenLoadingTheInvoiceView(AtomicityTestSupport.FailingStep steps) {
        Mockito.doAnswer(invocation -> {
            steps.run(() -> { });
            return null;
        }).when(invoiceViewLoader).loadInvoiceView(any());
    }

    private CreateAdHocInvoiceRequest request(String description) {
        return new CreateAdHocInvoiceRequest(FeeType.EXAM_FEE, ars(1500), LocalDate.now(), description, null, null);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }

    private Student persistStudent() {
        String username = "ADHOC" + System.nanoTime();
        Address address = addressRepository.save(new Address());
        Person person = new Person();
        person.setUsername(username);
        person.setDni(username);
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
        return studentRepository.save(student);
    }

    private CourseEnrollment persistCourseEnrollment() {
        Term term = TermFixtures.newTerm();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        term = termRepository.save(term);

        Course course = new Course();
        course.setName("Curso de prueba " + System.nanoTime());
        course.setTerm(term);
        course.setIsActive(StatusType.ACTIVE);
        course = courseRepository.save(course);

        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(persistStudent());
        enrollment.setCourse(course);
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        return courseEnrollmentRepository.save(enrollment);
    }

    private ProgramEnrollment persistProgramEnrollment() {
        Program program = new Program();
        program.setName("Carrera de prueba " + System.nanoTime());
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        program = programRepository.save(program);

        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(persistStudent());
        enrollment.setProgram(program);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        return programEnrollmentRepository.save(enrollment);
    }
}
