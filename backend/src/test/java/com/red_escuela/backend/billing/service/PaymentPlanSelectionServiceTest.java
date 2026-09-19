package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.billing.dto.CreatePaymentPlanOptionRequest;
import com.red_escuela.backend.billing.dto.SelectPaymentPlanRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.exception.InvalidPaymentPlanScopeException;
import com.red_escuela.backend.billing.exception.UpfrontWindowClosedException;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.billing.repository.PriceRevisionRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PaymentPlanSelectionServiceTest extends AbstractIntegrationTest {
    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentplan.CreatePaymentPlanOptionForCourseUseCase createPaymentPlanOptionForCourseUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentplan.CreatePaymentPlanOptionForProgramUseCase createPaymentPlanOptionForProgramUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentplan.SelectPaymentPlanForCourseUseCase selectPaymentPlanForCourseUseCase;

    @Autowired
    private com.red_escuela.backend.billing.usecase.paymentplan.SelectPaymentPlanForProgramUseCase selectPaymentPlanForProgramUseCase;

    @Autowired
    private InvoiceGenerationService invoiceGenerationService;

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
    private TermRepository termRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private PriceRevisionRepository priceRevisionRepository;

    @Autowired
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Person persistPersonBase(String dni) {
        Address address = addressRepository.save(new Address());
        Person person = new Person();
        person.setName("Persona");
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        return personRepository.save(person);
    }

    private void authenticateAsPerson(Person person) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(person.getId().toString(), null));
    }

    private Student persistStudent(String dni) {
        Institution institution = new Institution();
        institution.setName("Institución de prueba " + System.nanoTime());
        institution = institutionRepository.save(institution);

        Student student = new Student();
        student.setPerson(persistPersonBase(dni));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        return studentRepository.save(student);
    }

    private Course persistCourse(int durationMonths, BigDecimal price, Person director) {
        Term term = new Term();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        term = termRepository.save(term);

        Course course = new Course();
        course.setName("Curso de prueba " + System.nanoTime());
        course.setTerm(term);
        course.setCapacity(20);
        course.setIsActive(StatusType.ACTIVE);
        course.setDurationMonths(durationMonths);
        course = courseRepository.save(course);

        PriceRevision revision = new PriceRevision();
        revision.setCourse(course);
        revision.setFeeType(FeeType.RECURRING_FEE);
        revision.setDescription("Precio de prueba");
        revision.setMonthlyAmount(price);
        revision.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        revision.setCreatedBy(director);
        priceRevisionRepository.save(revision);

        return course;
    }

    private CourseEnrollment persistCourseEnrollment(Student student, Course course) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(student);
        enrollment.setCourse(course);
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(java.time.LocalDateTime.now());
        return courseEnrollmentRepository.save(enrollment);
    }

    private PaymentPlanOption persistUpfrontOption(Course course, boolean windowEnabled, LocalDate start, LocalDate end, Person director) {
        authenticateAsPerson(director);
        CreatePaymentPlanOptionRequest request = new CreatePaymentPlanOptionRequest(
                "Pago adelantado", PaymentPlanCadence.UPFRONT, null, windowEnabled, start, end);
        return createPaymentPlanOptionForCourseUseCase.create(course.getId(), request);
    }

    private PaymentPlanOption persistMonthlyOption(Course course, Person director) {
        authenticateAsPerson(director);
        CreatePaymentPlanOptionRequest request = new CreatePaymentPlanOptionRequest(
                "Mensual", PaymentPlanCadence.MONTHLY, null, false, null, null);
        return createPaymentPlanOptionForCourseUseCase.create(course.getId(), request);
    }

    @Test
    void sixMonthCourseOffersOneInstallmentOrSixInstallmentsAccordingToChosenPlan() {
        Person director = persistPersonBase("DIR4" + System.nanoTime());

        Course courseMonthly = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student studentMonthly = persistStudent("82200001");
        CourseEnrollment enrollmentMonthly = persistCourseEnrollment(studentMonthly, courseMonthly);
        PaymentPlanOption monthlyOption = persistMonthlyOption(courseMonthly, director);
        authenticateAsPerson(director);
        selectPaymentPlanForCourseUseCase.select(enrollmentMonthly.getId(), new SelectPaymentPlanRequest(monthlyOption.getId(), null));

        YearMonth mesInicial = YearMonth.now();
        for (int i = 0; i < 6; i++) {
            invoiceGenerationService.generateNextInstallmentForCourse(enrollmentMonthly, mesInicial.plusMonths(i));
        }
        assertThat(invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollmentMonthly.getId())).hasSize(6);

        Course courseUpfront = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student studentUpfront = persistStudent("82200002");
        CourseEnrollment enrollmentUpfront = persistCourseEnrollment(studentUpfront, courseUpfront);
        PaymentPlanOption upfrontOption = persistUpfrontOption(courseUpfront, true, LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), director);
        authenticateAsPerson(director);
        selectPaymentPlanForCourseUseCase.select(enrollmentUpfront.getId(), new SelectPaymentPlanRequest(upfrontOption.getId(), 6));

        invoiceGenerationService.generateNextInstallmentForCourse(enrollmentUpfront, mesInicial);
        assertThat(invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollmentUpfront.getId())).hasSize(6);
    }

    @Test
    void disabledOptionAlwaysRejectsTheSelection() {
        Person director = persistPersonBase("DIR29" + System.nanoTime());
        Course course = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student student = persistStudent("82200003");
        CourseEnrollment enrollment = persistCourseEnrollment(student, course);
        PaymentPlanOption option = persistUpfrontOption(course, false, null, null, director);
        authenticateAsPerson(director);

        assertThatThrownBy(() -> selectPaymentPlanForCourseUseCase.select(
                enrollment.getId(), new SelectPaymentPlanRequest(option.getId(), 6)))
                .isInstanceOf(UpfrontWindowClosedException.class);
    }

    @Test
    void dateBeforeTheWindowIsRejected() {
        Person director = persistPersonBase("DIR30" + System.nanoTime());
        Course course = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student student = persistStudent("82200004");
        CourseEnrollment enrollment = persistCourseEnrollment(student, course);
        PaymentPlanOption option = persistUpfrontOption(course, true, LocalDate.now().plusDays(10), LocalDate.now().plusDays(20), director);
        authenticateAsPerson(director);

        assertThatThrownBy(() -> selectPaymentPlanForCourseUseCase.select(
                enrollment.getId(), new SelectPaymentPlanRequest(option.getId(), 6)))
                .isInstanceOf(UpfrontWindowClosedException.class);
    }

    @Test
    void dateAfterTheWindowIsRejected() {
        Person director = persistPersonBase("DIR31" + System.nanoTime());
        Course course = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student student = persistStudent("82200005");
        CourseEnrollment enrollment = persistCourseEnrollment(student, course);
        PaymentPlanOption option = persistUpfrontOption(course, true, LocalDate.now().minusDays(20), LocalDate.now().minusDays(10), director);
        authenticateAsPerson(director);

        assertThatThrownBy(() -> selectPaymentPlanForCourseUseCase.select(
                enrollment.getId(), new SelectPaymentPlanRequest(option.getId(), 6)))
                .isInstanceOf(UpfrontWindowClosedException.class);
    }

    @Test
    void inclusiveStartOfWindowEdgeIsAccepted() {
        Person director = persistPersonBase("DIR32" + System.nanoTime());
        Course course = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student student = persistStudent("82200006");
        CourseEnrollment enrollment = persistCourseEnrollment(student, course);
        PaymentPlanOption option = persistUpfrontOption(course, true, LocalDate.now(), LocalDate.now().plusDays(10), director);
        authenticateAsPerson(director);

        var selection = selectPaymentPlanForCourseUseCase.select(
                enrollment.getId(), new SelectPaymentPlanRequest(option.getId(), 6));
        assertThat(selection).isNotNull();
        assertThat(selection.getPaymentPlanOption().getId()).isEqualTo(option.getId());
    }

    @Test
    void inclusiveEndOfWindowEdgeIsAccepted() {
        Person director = persistPersonBase("DIR32B" + System.nanoTime());
        Course course = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student student = persistStudent("82200019");
        CourseEnrollment enrollment = persistCourseEnrollment(student, course);
        PaymentPlanOption option = persistUpfrontOption(course, true, LocalDate.now().minusDays(10), LocalDate.now(), director);
        authenticateAsPerson(director);

        var selection = selectPaymentPlanForCourseUseCase.select(
                enrollment.getId(), new SelectPaymentPlanRequest(option.getId(), 6));
        assertThat(selection).isNotNull();
        assertThat(selection.getPaymentPlanOption().getId()).isEqualTo(option.getId());
    }

    @Test
    void scopeMonthsEqualToTheWholeYearGeneratesTheFullYearAtOnce() {
        Person director = persistPersonBase("DIR35" + System.nanoTime());
        Course course = persistCourse(10, BigDecimal.valueOf(10000), director);
        Student student = persistStudent("82200007");
        CourseEnrollment enrollment = persistCourseEnrollment(student, course);
        PaymentPlanOption option = persistUpfrontOption(course, true, LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), director);
        authenticateAsPerson(director);

        selectPaymentPlanForCourseUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(option.getId(), 10));
        invoiceGenerationService.generateNextInstallmentForCourse(enrollment, YearMonth.now());

        assertThat(invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId())).hasSize(10);
    }

    @Test
    void scopeMonthsZeroOrNegativeIsRejected() {
        Person director = persistPersonBase("DIR38" + System.nanoTime());
        Course course = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student student = persistStudent("82200008");
        CourseEnrollment enrollment = persistCourseEnrollment(student, course);
        PaymentPlanOption option = persistUpfrontOption(course, true, LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), director);
        authenticateAsPerson(director);

        assertThatThrownBy(() -> selectPaymentPlanForCourseUseCase.select(
                enrollment.getId(), new SelectPaymentPlanRequest(option.getId(), 0)))
                .isInstanceOf(InvalidPaymentPlanScopeException.class);

        assertThatThrownBy(() -> selectPaymentPlanForCourseUseCase.select(
                enrollment.getId(), new SelectPaymentPlanRequest(option.getId(), -3)))
                .isInstanceOf(InvalidPaymentPlanScopeException.class);
    }

    @Test
    void runningTheSameMonthTwiceOverAnUpfrontBlockDoesNotDuplicate() {
        Person director = persistPersonBase("DIR41" + System.nanoTime());
        Course course = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student student = persistStudent("82200009");
        CourseEnrollment enrollment = persistCourseEnrollment(student, course);
        PaymentPlanOption upfrontOption = persistUpfrontOption(course, true, LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), director);
        authenticateAsPerson(director);

        selectPaymentPlanForCourseUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(upfrontOption.getId(), 5));
        YearMonth mesInicial = YearMonth.now();
        invoiceGenerationService.generateNextInstallmentForCourse(enrollment, mesInicial);
        assertThat(invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId())).hasSize(5);

        invoiceGenerationService.generateNextInstallmentForCourse(enrollment, mesInicial);
        assertThat(invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId())).hasSize(5);
    }

    @Test
    void twoProgramsWithDifferentWindowsDoNotInterfere() {
        Person director = persistPersonBase("DIR33" + System.nanoTime());

        Course courseA = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student studentA = persistStudent("82200010");
        CourseEnrollment enrollmentA = persistCourseEnrollment(studentA, courseA);
        PaymentPlanOption optionA = persistUpfrontOption(courseA, true, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 30), director);

        Course courseB = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student studentB = persistStudent("82200011");
        CourseEnrollment enrollmentB = persistCourseEnrollment(studentB, courseB);
        PaymentPlanOption optionB = persistUpfrontOption(courseB, true, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 31), director);

        authenticateAsPerson(director);
        assertThatThrownBy(() -> selectPaymentPlanForCourseUseCase.select(
                enrollmentA.getId(), new SelectPaymentPlanRequest(optionA.getId(), 6)))
                .isInstanceOf(UpfrontWindowClosedException.class);
        assertThatThrownBy(() -> selectPaymentPlanForCourseUseCase.select(
                enrollmentB.getId(), new SelectPaymentPlanRequest(optionB.getId(), 6)))
                .isInstanceOf(UpfrontWindowClosedException.class);
    }

    @Test
    void monthlyOptionIsNeverAffectedByTheWindow() {
        Person director = persistPersonBase("DIR34" + System.nanoTime());
        Course course = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student student = persistStudent("82200012");
        CourseEnrollment enrollment = persistCourseEnrollment(student, course);
        PaymentPlanOption monthlyOption = persistMonthlyOption(course, director);
        authenticateAsPerson(director);

        var selection = selectPaymentPlanForCourseUseCase.select(
                enrollment.getId(), new SelectPaymentPlanRequest(monthlyOption.getId(), null));
        assertThat(selection).isNotNull();
    }

    @Test
    void scopeMonthsLowerGeneratesOnlyThatSpan() {
        Person director = persistPersonBase("DIR36" + System.nanoTime());
        Course course = persistCourse(10, BigDecimal.valueOf(10000), director);
        Student student = persistStudent("82200013");
        CourseEnrollment enrollment = persistCourseEnrollment(student, course);
        PaymentPlanOption option = persistUpfrontOption(course, true, LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), director);
        authenticateAsPerson(director);

        selectPaymentPlanForCourseUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(option.getId(), 5));
        invoiceGenerationService.generateNextInstallmentForCourse(enrollment, YearMonth.now());

        assertThat(invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId())).hasSize(5);
    }

    @Test
    void generatingMonthlyBeyondTheCourseDurationIsASilentNoOp() {
        Person director = persistPersonBase("DIRDUR" + System.nanoTime());
        Course course = persistCourse(3, BigDecimal.valueOf(12000), director);
        Student student = persistStudent("82200014");
        CourseEnrollment enrollment = persistCourseEnrollment(student, course);
        PaymentPlanOption mensual = persistMonthlyOption(course, director);
        authenticateAsPerson(director);
        selectPaymentPlanForCourseUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(mensual.getId(), null));

        YearMonth base = YearMonth.now();
        for (int i = 0; i < 5; i++) {
            invoiceGenerationService.generateNextInstallmentForCourse(enrollment, base.plusMonths(i));
        }

        assertThat(invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId())).hasSize(3);
    }

    @Test
    void generatingMonthlyOutsideTheCourseWindowIsASilentNoOp() {
        Person director = persistPersonBase("DIRVENT" + System.nanoTime());
        YearMonth base = YearMonth.now();
        Term term = new Term();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        term.setStartDate(base.atDay(1).atStartOfDay());
        term.setEndDate(base.plusMonths(2).atEndOfMonth().atTime(23, 59));
        term = termRepository.save(term);

        Course course = new Course();
        course.setName("Curso con ventana " + System.nanoTime());
        course.setTerm(term);
        course.setCapacity(20);
        course.setIsActive(StatusType.ACTIVE);
        course.setDurationMonths(6);
        course = courseRepository.save(course);

        PriceRevision revision = new PriceRevision();
        revision.setCourse(course);
        revision.setFeeType(FeeType.RECURRING_FEE);
        revision.setDescription("Precio de prueba");
        revision.setMonthlyAmount(BigDecimal.valueOf(12000));
        revision.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        revision.setCreatedBy(director);
        priceRevisionRepository.save(revision);

        Student student = persistStudent("82200015");
        CourseEnrollment enrollment = persistCourseEnrollment(student, course);
        PaymentPlanOption mensual = persistMonthlyOption(course, director);
        authenticateAsPerson(director);
        selectPaymentPlanForCourseUseCase.select(enrollment.getId(), new SelectPaymentPlanRequest(mensual.getId(), null));

        for (int i = 0; i < 5; i++) {
            invoiceGenerationService.generateNextInstallmentForCourse(enrollment, base.plusMonths(i));
        }

        assertThat(invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId())).hasSize(3);
    }

    @Test
    void aStudentCanChooseTheirOwnPaymentPlan() {
        Person director = persistPersonBase("DIROWN1" + System.nanoTime());
        Course course = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student student = persistStudent("82200016");
        CourseEnrollment enrollment = persistCourseEnrollment(student, course);
        PaymentPlanOption mensual = persistMonthlyOption(course, director);

        authenticateAsPerson(student.getPerson());
        var selection = selectPaymentPlanForCourseUseCase.select(
                enrollment.getId(), new SelectPaymentPlanRequest(mensual.getId(), null));

        assertThat(selection.getPaymentPlanOption().getId()).isEqualTo(mensual.getId());
        assertThat(selection.getCourseEnrollment().getId()).isEqualTo(enrollment.getId());
        assertThat(selection.getCreatedBy().getId()).isEqualTo(student.getPerson().getId());
    }

    @Test
    void aStudentCannotChooseAnotherStudentsPaymentPlan() {
        Person director = persistPersonBase("DIROWN2" + System.nanoTime());
        Course course = persistCourse(6, BigDecimal.valueOf(15000), director);
        Student dueño = persistStudent("82200017");
        Student intruso = persistStudent("82200018");
        CourseEnrollment enrollment = persistCourseEnrollment(dueño, course);
        PaymentPlanOption mensual = persistMonthlyOption(course, director);

        authenticateAsPerson(intruso.getPerson());
        assertThatThrownBy(() -> selectPaymentPlanForCourseUseCase.select(
                enrollment.getId(), new SelectPaymentPlanRequest(mensual.getId(), null)))
                .isInstanceOf(com.red_escuela.backend.exception.NotOwnEnrollmentException.class);
    }
}
