package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.billing.account.domain.PaymentAllocation;
import com.red_escuela.backend.billing.account.repository.PaymentAllocationRepository;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.collections.service.OverdueInvoiceTransitionService;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.CourseReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ProgramReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.billing.configuration.domain.ReminderMilestone;
import com.red_escuela.backend.billing.configuration.domain.ReminderNotice;
import com.red_escuela.backend.billing.configuration.domain.ReminderRepetition;
import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderSendingOverrides;
import com.red_escuela.backend.billing.configuration.domain.TeachingGroup;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.configuration.usecase.ProposeBillingConfigurationChangeUseCase;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.service.EnrollmentBillingComplianceRowProcessor;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.notification.delivery.service.EmailDeliveryDispatchJob;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.time.ApplicationTime;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@ActiveProfiles("test")
abstract class AbstractReminderTimelineIT extends AbstractIntegrationTest {
    private static final StatusChangeActor ACTOR = new StatusChangeActor(null, "reminder-timeline-it");

    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected TransactionTemplate transactionTemplate;
    @Autowired
    protected ProposeBillingConfigurationChangeUseCase proposeChange;
    @Autowired
    private CollectionReminderPlanningStep planningStep;
    @Autowired
    private OverdueInvoiceTransitionService overdueTransition;
    @Autowired
    private EnrollmentBillingComplianceRowProcessor complianceProcessor;
    @Autowired
    private BillingConfigurationReader configurationReader;
    @Autowired
    private EmailDeliveryDispatchJob dispatchJob;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private PaymentAllocationRepository allocationRepository;
    @Autowired
    private StudentCurrentAccountService accountService;
    @Autowired
    private CurrentAccountAllocationService allocationService;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private RoleRepository roleRepository;
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

    private Institution institution;
    private Term term;
    private Person director;

    @BeforeEach
    void setUpTimelineScenario() {
        ApplicationTime.useClock(MutableClock.INSTANCE);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 2, 26, 12, 0));
        institution = new Institution();
        institution.setName("Instituto linea de tiempo " + System.nanoTime());
        institution = institutionRepository.save(institution);
        term = new Term();
        term.setYear(2027 + (int) (System.nanoTime() % 1000));
        term.setStatus(StatusType.ACTIVE);
        term = termRepository.save(term);
        director = newPerson("director", "DIRECTOR", true);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(director.getId().toString(), null, "ROLE_DIRECTOR"));
    }

    @AfterEach
    void tearDownTimelineScenario() {
        SecurityContextHolder.clearContext();
        MutableClock.INSTANCE.reset();
    }

    protected ReminderTimelineHarness newHarness() {
        return new ReminderTimelineHarness(jdbc, planningStep, overdueTransition, complianceProcessor, configurationReader, dispatchJob);
    }

    protected Person newPerson(String prefix, String role, boolean emailVerified) {
        return transactionTemplate.execute(status -> {
            Person person = new Person();
            String username = prefix + "." + System.nanoTime();
            person.setUsername(username);
            person.setDni(Long.toString(Math.abs((long) username.hashCode())));
            person.setName("Persona");
            person.setLastname("Linea");
            person.setEmail(username + "@example.com");
            person.setEmailVerified(emailVerified);
            person.setAddress(addressRepository.save(new Address()));
            person.setStatus(StatusType.ACTIVE);
            if (role != null) {
                person.getRoles().add(roleRepository.findByName(role).orElseThrow());
            }
            return personRepository.save(person);
        });
    }

    protected Student newStudent() {
        Student student = new Student();
        student.setPerson(newPerson("alumno", null, true));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        return studentRepository.save(student);
    }

    protected Course newCourse() {
        Course course = new Course();
        course.setName("Curso linea " + System.nanoTime());
        course.setTerm(term);
        course.setIsActive(StatusType.ACTIVE);
        return courseRepository.save(course);
    }

    protected CourseEnrollment enrollInCourse(Student student, Course course) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(student);
        enrollment.setCourse(course);
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.of(2027, 2, 1, 10, 0));
        return courseEnrollmentRepository.save(enrollment);
    }

    protected ProgramEnrollment enrollInProgram(Student student) {
        Program program = new Program();
        program.setName("Carrera linea " + System.nanoTime());
        program.setTotalYears(3);
        program.setTotalCreditsRequired(0);
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(programRepository.save(program));
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.of(2027, 2, 1, 10, 0));
        return programEnrollmentRepository.save(enrollment);
    }

    protected Invoice installmentOf(CourseEnrollment enrollment, LocalDate issueDate, LocalDate dueDate) {
        Invoice invoice = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate).withBaseAmount(ars(100000)).build();
        return withDates(invoice, issueDate, dueDate);
    }

    protected Invoice installmentOf(ProgramEnrollment enrollment, LocalDate issueDate, LocalDate dueDate) {
        Invoice invoice = BillingTestFixtures.invoice().forProgramEnrollment(enrollment).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate).withBaseAmount(ars(100000)).build();
        return withDates(invoice, issueDate, dueDate);
    }

    protected Invoice enrollmentFeeOf(ProgramEnrollment enrollment, LocalDate issueDate, LocalDate dueDate) {
        Invoice invoice = BillingTestFixtures.invoice().forProgramEnrollment(enrollment).ofType(FeeType.ENROLLMENT_FEE).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate)
                .withBaseAmount(ars(50000)).pending().build();
        return withDates(invoice, issueDate, dueDate);
    }

    private Invoice withDates(Invoice invoice, LocalDate issueDate, LocalDate dueDate) {
        jdbc.update("update invoice set issue_date = ?, due_date = ? where id = ?", issueDate, dueDate, invoice.getId());
        return invoice;
    }

    protected Invoice invoiceById(Long invoiceId) {
        return invoiceRepository.findInvoiceWithLinesById(invoiceId).orElseThrow();
    }

    protected void pay(Invoice invoice, LocalDate day) {
        BillingTestFixtures.payInvoiceInFullWithMoneyOf(invoice, day, day);
    }

    protected void revertPayment(Invoice invoice, LocalDate businessDate, int graceDays) {
        PaymentAllocation allocation = allocationRepository.findCurrentAllocationByInvoiceId(invoice.getId()).orElseThrow();
        transactionTemplate.executeWithoutResult(status -> allocationService.reverseAllocation(accountService.lockStudentCurrentAccount(allocation.getStudentCurrentAccountId()),
                allocation, businessDate, graceDays, "Contracargo", ACTOR));
    }

    protected Long uploadReceipt(Invoice invoice) {
        return jdbc.queryForObject("insert into bank_transfer_receipt (invoice_id, file_path, uploaded_at, status, version) values (?, 'comprobante.pdf', now(), 'PENDING_REVIEW', 0) returning id",
                Long.class, invoice.getId());
    }

    protected void rejectReceipt(Long receiptId) {
        jdbc.update("update bank_transfer_receipt set status = 'REJECTED' where id = ?", receiptId);
    }

    protected String paymentStatusOf(Invoice invoice) {
        return jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoice.getId());
    }

    protected static ReminderNotice notice(String key, ReminderMessageType type, ReminderMilestone milestone, int offsetDays, EnumSet<ReminderChannel> channels,
                                           ReminderRepetition repetition) {
        return new ReminderNotice(key, type, milestone, offsetDays, channels, repetition);
    }

    protected static List<ReminderNotice> defaultNoticesWithout(String removedKey) {
        return ReminderSchedule.initial().notices().stream().filter(notice -> !notice.noticeKey().equals(removedKey)).toList();
    }

    protected void publishCourseSchedule(Course course, LocalDate effectiveFrom, List<ReminderNotice> notices) {
        proposeChange.proposeChange(ConfigurationTopic.ofReminderScheduleException(TeachingGroup.MONTHLY_COURSES, course.getId()),
                new CourseReminderSchedule(new ReminderSchedule(false, notices, ReminderSendingOverrides.none())), effectiveFrom, "motivo");
    }

    protected void publishProgramSchedule(Program program, LocalDate effectiveFrom, List<ReminderNotice> notices) {
        proposeChange.proposeChange(ConfigurationTopic.ofReminderScheduleException(TeachingGroup.PROGRAMS, program.getId()),
                new ProgramReminderSchedule(new ReminderSchedule(false, notices, ReminderSendingOverrides.none())), effectiveFrom, "motivo");
    }
}
