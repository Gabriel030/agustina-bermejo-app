package com.red_escuela.backend.characterization.enrollment;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
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
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.concurrent.atomic.AtomicLong;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
abstract class WithdrawalP2CharacterizationSupport extends AbstractIntegrationTest {

    private static final AtomicLong SEQUENCE = new AtomicLong(System.nanoTime() % 1_000_000L);

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected EntityManager entityManager;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected AddressRepository addressRepository;
    @Autowired
    protected PersonRepository personRepository;
    @Autowired
    protected InstitutionRepository institutionRepository;
    @Autowired
    protected StudentRepository studentRepository;
    @Autowired
    protected ProgramRepository programRepository;
    @Autowired
    protected ProgramEnrollmentRepository programEnrollmentRepository;
    @Autowired
    protected CourseEnrollmentRepository courseEnrollmentRepository;
    @Autowired
    protected RoleRepository roleRepository;
    @Autowired
    protected JwtService jwtService;

    protected Person persistPerson(String prefix) {
        long number = SEQUENCE.incrementAndGet();
        Address address = addressRepository.save(new Address());
        Person person = new Person();
        person.setName("Persona");
        person.setLastname("Prueba");
        person.setUsername(prefix + number);
        person.setDni(String.valueOf(60_000_000L + number));
        person.setEmail(prefix + number + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        return personRepository.save(person);
    }

    protected Student persistStudent() {
        Institution institution = new Institution();
        institution.setName("Instituto " + SEQUENCE.incrementAndGet());
        Student student = new Student();
        student.setPerson(persistPerson("withdrawal.p2.student"));
        student.setInstitution(institutionRepository.save(institution));
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        return studentRepository.save(student);
    }

    protected String directorBearer() {
        Person director = persistPerson("withdrawal.p2.director");
        director.getRoles().add(roleRepository.findByName("DIRECTOR").orElseThrow());
        personRepository.save(director);
        entityManager.flush();
        return "Bearer " + jwtService.generateAccessToken(director);
    }

    protected Program persistProgram() {
        Program program = new Program();
        program.setName("Carrera P2 " + SEQUENCE.incrementAndGet());
        program.setTotalYears(3);
        program.setTotalCreditsRequired(0);
        return programRepository.save(program);
    }

    protected ProgramEnrollment persistProgramEnrollment(Student student, Program program, ProgramEnrollmentStatus status) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(status);
        enrollment.setEnrollmentDate(LocalDateTime.now().minusMonths(6));
        return programEnrollmentRepository.save(enrollment);
    }

    protected CourseEnrollment persistCourseEnrollment(Student student, EnrollmentStatus status) {
        Term term = TermFixtures.newTerm();
        term.setYear(2027);
        term.setDescription("2027-1C " + SEQUENCE.incrementAndGet());
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        Course course = new Course();
        course.setTerm(term);
        course.setName("Curso P2 " + SEQUENCE.incrementAndGet());
        course.setIsActive(StatusType.ACTIVE);
        entityManager.persist(course);
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(student);
        enrollment.setCourse(course);
        enrollment.setStatus(status);
        enrollment.setEnrollmentDate(LocalDate.now().minusMonths(6).atTime(10, 0));
        return courseEnrollmentRepository.save(enrollment);
    }

    protected Invoice persistPendingInstallment(ProgramEnrollment enrollment, YearMonth period, LocalDate dueDate, int amount) {
        return BillingTestFixtures.invoice().forProgramEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(period)
                .dueOn(dueDate).withBaseAmount(ars(BigDecimal.valueOf(amount))).build();
    }

    protected Invoice persistOverdueInstallment(ProgramEnrollment enrollment, int amount) {
        Invoice invoice = persistPendingInstallment(enrollment, YearMonth.now().minusMonths(3), LocalDate.now().minusMonths(3), amount);
        return BillingTestFixtures.markInvoiceAsOverdue(invoice);
    }

    protected String paymentStatusOf(Long invoiceId) {
        entityManager.flush();
        return jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoiceId);
    }

    protected long creditNoteCount() {
        entityManager.flush();
        return jdbc.queryForObject("select count(*) from credit_note", Long.class);
    }
}
