package com.red_escuela.backend.enrollment;

import static com.red_escuela.backend.billing.support.TransferReceiptFixtures.newIdempotencyKey;
import static com.red_escuela.backend.billing.support.TransferReceiptFixtures.signedInAs;
import static com.red_escuela.backend.billing.support.TransferReceiptFixtures.transferReceiptOf;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;

import com.red_escuela.backend.academicstructure.domain.CohortIntake;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.planning.TestTemplateTerms;
import com.red_escuela.backend.billing.support.PriceFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.support.StudentDocumentFixtures;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.ApproveProgramEnrollmentApplicationCommand;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.ApproveProgramEnrollmentApplicationUseCase;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.RequestProgramEnrollmentApplicationCommand;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.RequestProgramEnrollmentApplicationUseCase;
import com.red_escuela.backend.identity.repository.RoleRepository;
import java.util.List;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentApplicationService;
import com.red_escuela.backend.enrollment.service.CourseWaitlistExpiration;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class EnrollmentWithBillingOffIT extends AbstractIntegrationTest {

    @Autowired private CourseEnrollmentApplicationService applicationService;
    @Autowired private CourseWaitlistExpiration courseWaitlistExpiration;
    @Autowired private CourseEnrollmentRepository courseEnrollmentRepository;
    @Autowired private ProgramEnrollmentRepository programEnrollmentRepository;
    @Autowired private RequestProgramEnrollmentApplicationUseCase requestProgramUseCase;
    @Autowired private ApproveProgramEnrollmentApplicationUseCase approveProgramUseCase;
    @Autowired private RoleRepository roleRepository;
    @Autowired private TransactionTemplate transactions;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FeatureSnapshotHolder snapshotHolder;

    @AfterEach
    void restoreBillingFull() {
        SecurityContextHolder.clearContext();
        jdbc.update("update platform_feature set enabled = true, mode = 'FULL', version = 0 where code = 'BILLING'");
        snapshotHolder.refresh();
    }

    @Test
    void courseEnrollmentSucceedsWithoutFeeWhenBillingIsOff() {
        switchBillingTo("OFF", false);
        Student student = persistStudent();
        Course course = persistPricedCourse(20);

        CourseEnrollmentApplication application = applicationService.requestWithPayment(signedInAs(student),
                new CreateCourseEnrollmentRequest(course.getId(), null), null, newIdempotencyKey());

        assertThat(application.getCourseEnrollment()).isNotNull();
        assertThat(application.getInvoiceId()).isNull();
        assertThat(courseEnrollmentRepository.findByStudentId(student.getId())).hasSize(1);
        assertThat(billingRowsOf(student)).isZero();
    }

    @Test
    void courseWaitlistExpiryRunsWithoutRefundWhenBillingIsOff() {
        switchBillingTo("OFF", false);
        Student seated = persistStudent();
        Student waitlisted = persistStudent();
        Course course = persistPricedCourse(1);
        applicationService.requestWithPayment(signedInAs(seated), new CreateCourseEnrollmentRequest(course.getId(), null), null, newIdempotencyKey());
        CourseEnrollment waiting = applicationService.enrollByStaff(waitlisted.getId(), new CreateCourseEnrollmentRequest(course.getId(), null));
        assertThat(waiting.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);

        courseWaitlistExpiration.expire(course.getId());

        assertThat(courseEnrollmentRepository.findById(waiting.getId()).orElseThrow().getStatus()).isEqualTo(EnrollmentStatus.WAITLIST_EXPIRED);
        assertThat(billingRowsOf(waitlisted)).isZero();
        assertThat(billingRowsOf(seated)).isZero();
    }

    @Test
    void enrollmentFeeIsRequiredAgainWhenBillingIsFull() {
        Student student = persistStudent();
        Course course = persistPricedCourse(20);

        CourseEnrollmentApplication application = applicationService.requestWithPayment(signedInAs(student),
                new CreateCourseEnrollmentRequest(course.getId(), null), transferReceiptOf(15000), newIdempotencyKey());

        assertThat(application.getInvoiceId()).isNotNull();
        assertThat(application.getCourseEnrollment()).isNull();
        assertThat(billingRowsOf(student)).isPositive();
    }

    @Test
    void careerApprovalEnrollsDirectlyWithoutAdmissionInvoiceWhenBillingIsOff() {
        switchBillingTo("OFF", false);
        ProgramEnrollmentApplication application = requestCareerApplication();

        ProgramEnrollmentApplication approved = approveCareerApplication(application);

        assertThat(approved.getStatus()).isEqualTo(ProgramEnrollmentApplicationStatus.APPROVED);
        assertThat(approved.getAdmissionInvoice()).isNull();
        assertThat(programEnrollmentRepository.findByStudentIdAndProgramIdAndStatusIn(application.getStudent().getId(),
                application.getProgram().getId(), List.of(ProgramEnrollmentStatus.ENROLLED))).hasSize(1);
        assertThat(billingRowsOf(application.getStudent())).isZero();
    }

    @Test
    void careerApprovalIssuesAdmissionInvoiceAndDefersEnrollmentWhenBillingIsFull() {
        ProgramEnrollmentApplication application = requestCareerApplication();

        ProgramEnrollmentApplication approved = approveCareerApplication(application);

        assertThat(approved.getAdmissionInvoice()).isNotNull();
        assertThat(programEnrollmentRepository.findByStudentIdAndProgramIdAndStatusIn(application.getStudent().getId(),
                application.getProgram().getId(), List.of(ProgramEnrollmentStatus.ENROLLED))).isEmpty();
    }

    private ProgramEnrollmentApplication approveCareerApplication(ProgramEnrollmentApplication application) {
        Person preceptor = transactions.execute(status -> {
            Person person = persistPerson("PREC" + System.nanoTime());
            person.getRoles().add(roleRepository.findByName("PRECEPTOR").orElseThrow());
            return person;
        });
        return transactions.execute(status -> {
            ProgramEnrollmentApplication approved = approveProgramUseCase.approve(new ApproveProgramEnrollmentApplicationCommand(application.getId(), preceptor));
            return approved;
        });
    }

    private ProgramEnrollmentApplication requestCareerApplication() {
        return transactions.execute(status -> {
            Institution institution = new Institution();
            institution.setName("Instituto " + System.nanoTime());
            entityManager.persist(institution);
            Student student = new Student();
            student.setPerson(persistPerson("8" + System.nanoTime()));
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            Program program = new Program();
            program.setVisibleInCatalog(true);
            program.setName("Carrera " + System.nanoTime());
            program.setTotalYears(3);
            program.setTotalCreditsRequired(0);
            entityManager.persist(program);
            Person director = persistPerson("DIR" + System.nanoTime());
            Term term = TermFixtures.newTerm();
            term.setYear(2026);
            term.setStatus(StatusType.ACTIVE);
            entityManager.persist(term);
            ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
            template.setProgram(program);
            template.setTerm(term);
            TestTemplateTerms.setRecurringFeeInvoicesCount(template, 10);
            template.setCreatedBy(director);
            entityManager.persist(template);
            TestTemplateTerms.publish(entityManager, template);
            ProgramCohort cohort = new ProgramCohort();
            cohort.markOpened(java.time.LocalDateTime.of(2000, 1, 1, 0, 0));
            cohort.setTemplateId(template.getId());
            cohort.setProgramId(program.getId());
            cohort.setTermId(term.getId());
            cohort.setIntake(CohortIntake.FIRST);
            cohort.setAdmissionStart(LocalDate.now().minusDays(30));
            cohort.setAdmissionEnd(LocalDate.now().plusDays(30));
            cohort.setCursadaStart(LocalDate.now());
            cohort.setCreatedBy(director);
            entityManager.persist(cohort);
            PriceFixtures.persistPriceVersion(program.getId(), null, FeeType.ENROLLMENT_FEE, "Matrícula de prueba",
                    ars(BigDecimal.valueOf(50000)), LocalDate.now().minusDays(1), director.getId());
            StudentDocumentFixtures.persistAllRequiredDocuments(entityManager, student);
            entityManager.flush();
            return requestProgramUseCase.request(new RequestProgramEnrollmentApplicationCommand(student, program.getId(), cohort.getId()));
        });
    }

    private int billingRowsOf(Student student) {
        return jdbc.queryForObject("select (select count(*) from invoice where student_current_account_id in (select id from student_current_account where student_id = ?))"
                + " + (select count(*) from student_current_account where student_id = ?)", Integer.class, student.getId(), student.getId());
    }

    private void switchBillingTo(String mode, boolean enabled) {
        jdbc.update("update platform_feature set enabled = ?, mode = ? where code = 'BILLING'", enabled, mode);
        snapshotHolder.refresh();
    }

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
        return transactions.execute(status -> {
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

    private Course persistPricedCourse(int capacity) {
        return transactions.execute(status -> {
            Term term = TermFixtures.newTerm();
            term.setYear(2026);
            term.setStatus(StatusType.ACTIVE);
            entityManager.persist(term);
            Course course = new Course();
            course.setVisibleInCatalog(true);
            course.setName("Curso " + System.nanoTime());
            course.setTerm(term);
            course.setCapacity(capacity);
            course.setIsActive(StatusType.ACTIVE);
            entityManager.persist(course);
            PriceFixtures.persistPriceVersion(null, course.getId(), FeeType.MONTHLY_INSTALLMENT, "Precio de prueba",
                    ars(BigDecimal.valueOf(15000)), LocalDate.now().minusDays(1), persistPerson("DIR" + System.nanoTime()).getId());
            return course;
        });
    }
}
