package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MonthlyInstallmentPayerCounterIT extends AbstractIntegrationTest {
    private static final YearMonth SEPTEMBER = YearMonth.of(2027, 9);
    private static final YearMonth MARCH = YearMonth.of(2027, 3);

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private MonthlyInstallmentPayerCounter counter;

    private Person creator;
    private Institution institution;
    private Program program;
    private Program otherProgram;
    private PaymentPlanOption monthlyProgramOption;
    private PaymentPlanOption annualProgramOption;
    private PaymentPlanOption otherProgramOption;
    private Course course;
    private Course otherCourse;
    private PaymentPlanOption monthlyCourseOption;
    private PaymentPlanOption otherCourseOption;

    @BeforeEach
    void setUp() {
        creator = person();
        institution = new Institution();
        institution.setName("Instituto " + System.nanoTime());
        entityManager.persist(institution);
        program = program();
        otherProgram = program();
        monthlyProgramOption = option(program, null, PaymentPlanCadence.MONTHLY);
        annualProgramOption = option(program, null, PaymentPlanCadence.ANNUAL_UPFRONT);
        otherProgramOption = option(otherProgram, null, PaymentPlanCadence.MONTHLY);
        Term term = new Term();
        term.setYear(2027);
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        course = course(term);
        otherCourse = course(term);
        monthlyCourseOption = option(null, course, PaymentPlanCadence.MONTHLY);
        otherCourseOption = option(null, otherCourse, PaymentPlanCadence.MONTHLY);
    }

    private Person person() {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String username = "payer." + System.nanoTime();
        person.setUsername(username);
        person.setDni(Long.toString(Math.abs((long) username.hashCode())));
        person.setName("Persona");
        person.setLastname("Prueba");
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        return person;
    }

    private Student student() {
        Student student = new Student();
        student.setPerson(person());
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private Program program() {
        Program created = new Program();
        created.setName("Carrera " + System.nanoTime());
        created.setTotalYears(3);
        created.setTotalCreditsRequired(10);
        entityManager.persist(created);
        return created;
    }

    private Course course(Term term) {
        Course created = new Course();
        created.setName("Curso " + System.nanoTime());
        created.setTerm(term);
        created.setIsActive(StatusType.ACTIVE);
        entityManager.persist(created);
        return created;
    }

    private PaymentPlanOption option(Program ofProgram, Course ofCourse, PaymentPlanCadence cadence) {
        PaymentPlanOption option = new PaymentPlanOption();
        option.setProgram(ofProgram);
        option.setCourse(ofCourse);
        option.setDescription(cadence.name());
        option.setCadence(cadence);
        option.setCreatedBy(creator);
        entityManager.persist(option);
        return option;
    }

    private void programEnrollment(Program ofProgram, ProgramEnrollmentStatus status, EnrollmentPaymentStatus payment, PaymentPlanOption option, Integer scopeMonths) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student());
        enrollment.setProgram(ofProgram);
        enrollment.setStatus(status);
        enrollment.setPaymentStatus(payment);
        enrollment.setEnrollmentDate(LocalDateTime.of(2027, 3, 1, 10, 0));
        entityManager.persist(enrollment);
        if (option != null) {
            PaymentPlanSelection selection = new PaymentPlanSelection();
            selection.setProgramEnrollment(enrollment);
            selection.setPaymentPlanOption(option);
            selection.setEffectiveFrom(MARCH);
            selection.setScopeMonths(scopeMonths);
            selection.setCreatedBy(creator);
            entityManager.persist(selection);
        }
    }

    private void courseEnrollment(Course ofCourse, EnrollmentStatus status, EnrollmentPaymentStatus payment, PaymentPlanOption option) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(student());
        enrollment.setCourse(ofCourse);
        enrollment.setStatus(status);
        enrollment.setPaymentStatus(payment);
        enrollment.setEnrollmentDate(LocalDateTime.of(2027, 3, 1, 10, 0));
        entityManager.persist(enrollment);
        PaymentPlanSelection selection = new PaymentPlanSelection();
        selection.setCourseEnrollment(enrollment);
        selection.setPaymentPlanOption(option);
        selection.setEffectiveFrom(MARCH);
        selection.setCreatedBy(creator);
        entityManager.persist(selection);
    }

    @Test
    @Tag("D-327")
    void onlyEnrolledProgramEnrollmentsThatIssueNormalMonthlyInstallmentsAreCountedAndPausedOnesAreNotBecauseTheyAreNotBilledByDefault() {
        for (int paying = 0; paying < 4; paying++) {
            programEnrollment(program, ProgramEnrollmentStatus.ENROLLED, EnrollmentPaymentStatus.UP_TO_DATE, monthlyProgramOption, null);
        }
        programEnrollment(program, ProgramEnrollmentStatus.ENROLLED, EnrollmentPaymentStatus.OVERDUE, monthlyProgramOption, null);
        programEnrollment(program, ProgramEnrollmentStatus.PAUSED, EnrollmentPaymentStatus.UP_TO_DATE, monthlyProgramOption, null);
        programEnrollment(program, ProgramEnrollmentStatus.ENROLLED, EnrollmentPaymentStatus.SUSPENDED, monthlyProgramOption, null);
        programEnrollment(program, ProgramEnrollmentStatus.ENROLLED, EnrollmentPaymentStatus.SUSPENDED, monthlyProgramOption, null);
        programEnrollment(program, ProgramEnrollmentStatus.ENROLLED, EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT, monthlyProgramOption, null);
        programEnrollment(program, ProgramEnrollmentStatus.WITHDRAWN, EnrollmentPaymentStatus.UP_TO_DATE, monthlyProgramOption, null);
        programEnrollment(program, ProgramEnrollmentStatus.COMPLETED, EnrollmentPaymentStatus.UP_TO_DATE, monthlyProgramOption, null);
        for (int paidYear = 0; paidYear < 3; paidYear++) {
            programEnrollment(program, ProgramEnrollmentStatus.ENROLLED, EnrollmentPaymentStatus.UP_TO_DATE, annualProgramOption, 12);
        }
        programEnrollment(program, ProgramEnrollmentStatus.ENROLLED, EnrollmentPaymentStatus.UP_TO_DATE, null, null);
        programEnrollment(otherProgram, ProgramEnrollmentStatus.ENROLLED, EnrollmentPaymentStatus.UP_TO_DATE, otherProgramOption, null);
        entityManager.flush();

        assertThat(counter.countProgramEnrollmentsPayingMonthlyInstallments(program.getId(), SEPTEMBER)).isEqualTo(5);
    }

    @Test
    @Tag("D-327")
    void anAnnualPlanThatAlreadyEndedPaysInstallmentsAgainButOneStillCoveredDoesNot() {
        programEnrollment(program, ProgramEnrollmentStatus.ENROLLED, EnrollmentPaymentStatus.UP_TO_DATE, annualProgramOption, 6);
        programEnrollment(program, ProgramEnrollmentStatus.ENROLLED, EnrollmentPaymentStatus.UP_TO_DATE, annualProgramOption, 7);
        programEnrollment(program, ProgramEnrollmentStatus.ENROLLED, EnrollmentPaymentStatus.SUSPENDED, annualProgramOption, 6);
        entityManager.flush();

        assertThat(counter.countProgramEnrollmentsPayingMonthlyInstallments(program.getId(), SEPTEMBER)).isEqualTo(1);
        assertThat(counter.countProgramEnrollmentsPayingMonthlyInstallments(program.getId(), YearMonth.of(2027, 10))).isEqualTo(2);
    }

    @Test
    @Tag("D-327")
    void onlyEnrolledCourseEnrollmentsThatArePayingAreCountedAndInProgressIsNotBecauseTheMonthlyIssuanceSkipsIt() {
        courseEnrollment(course, EnrollmentStatus.ENROLLED, EnrollmentPaymentStatus.UP_TO_DATE, monthlyCourseOption);
        courseEnrollment(course, EnrollmentStatus.ENROLLED, EnrollmentPaymentStatus.OVERDUE, monthlyCourseOption);
        for (EnrollmentStatus status : new EnrollmentStatus[]{EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR, EnrollmentStatus.APPROVED, EnrollmentStatus.FAILED,
                EnrollmentStatus.WITHDRAWN, EnrollmentStatus.WAITLISTED, EnrollmentStatus.WAITLIST_EXPIRED}) {
            courseEnrollment(course, status, EnrollmentPaymentStatus.UP_TO_DATE, monthlyCourseOption);
        }
        courseEnrollment(course, EnrollmentStatus.ENROLLED, EnrollmentPaymentStatus.SUSPENDED, monthlyCourseOption);
        courseEnrollment(course, EnrollmentStatus.ENROLLED, EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT, monthlyCourseOption);
        courseEnrollment(otherCourse, EnrollmentStatus.ENROLLED, EnrollmentPaymentStatus.UP_TO_DATE, otherCourseOption);
        entityManager.flush();

        assertThat(counter.countCourseEnrollmentsPayingMonthlyInstallments(course.getId(), SEPTEMBER)).isEqualTo(2);
    }
}
