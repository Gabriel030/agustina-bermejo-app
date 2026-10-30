package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestStatus;
import com.red_escuela.backend.billing.authorization.domain.IllegalAuthorizationRequestTransitionException;
import com.red_escuela.backend.billing.authorization.repository.AuthorizationRequestRepository;
import com.red_escuela.backend.billing.authorization.service.AuthorizationRequestExpirer;
import com.red_escuela.backend.billing.authorization.usecase.ApproveAuthorizationRequestUseCase;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.discount.DiscountDraftBuilder;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrantPreview;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrantRequest;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountStatus;
import com.red_escuela.backend.billing.discount.exception.CurrencyMismatchException;
import com.red_escuela.backend.billing.discount.exception.DiscountInvoiceTypeMismatchException;
import com.red_escuela.backend.billing.discount.exception.EnrollmentFeeAlreadyIssuedException;
import com.red_escuela.backend.billing.discount.exception.IllegalStudentDiscountTransitionException;
import com.red_escuela.backend.billing.discount.exception.InvalidScholarshipPeriodException;
import com.red_escuela.backend.billing.discount.exception.InvalidStudentDiscountException;
import com.red_escuela.backend.billing.discount.exception.RevocationReasonRequiredException;
import com.red_escuela.backend.billing.discount.exception.ScholarshipStartBeforeNextInstallmentException;
import com.red_escuela.backend.billing.discount.exception.StudentDiscountOverlapException;
import com.red_escuela.backend.billing.discount.exception.StudentDiscountProposalExpiredException;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.operation.IssuingScenarioFactory;
import com.red_escuela.backend.billing.invoice.service.InvoiceIssuingService;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Tag("D-238")
@Tag("D-284")
@Tag("D-389")
@Tag("D-282")
@Tag("D-217")
@Tag("D-384")
@Tag("D-129")
@Tag("D-097")
@Tag("D-401")
@Tag("D-402")
class StudentDiscountGrantingIT extends AbstractIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);
    private static final StatusChangeActor ACTOR = StatusChangeActor.ofProcess("TEST_STUDENT_DISCOUNT");

    @Autowired
    private BillingOperationPipeline pipeline;
    @Autowired
    private GrantStudentDiscountOperation grantOperation;
    @Autowired
    private RevokeStudentDiscountOperation revokeOperation;
    @Autowired
    private CreateDiscountDefinitionOperation createDefinitionOperation;
    @Autowired
    private CloseDiscountDefinitionOperation closeDefinitionOperation;
    @Autowired
    private StudentDiscountRepository repository;
    @Autowired
    private ApproveAuthorizationRequestUseCase approveRequest;
    @Autowired
    private AuthorizationRequestRepository requestRepository;
    @Autowired
    private AuthorizationRequestExpirer expirer;
    @Autowired
    private InvoiceIssuingService issuingService;
    @Autowired
    private IssuingScenarioFactory factory;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private EntityManager entityManager;

    private Person director;
    private Person administrative;
    private IssuingScenarioFactory.Offering offering;
    private IssuingScenarioFactory.Learner learner;
    private BillingInvariantChecker invariants;
    private Long courseStudentId;
    private final List<Long> createdAccountIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        invariants = BillingInvariantChecker.startingNow(jdbc);
        MutableClock.INSTANCE.setDate(TODAY);
        director = persistPerson("sd.director", "DIRECTOR");
        administrative = persistPerson("sd.administrative", "ADMINISTRATIVO");
        offering = factory.newProgram(Currency.ARS, 80000, 50000);
        learner = factory.newLearner(offering, null);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        deleteDiscountsOfStudent(learner.studentId());
        createdAccountIds.forEach(accountId -> {
            jdbc.update("delete from invoice_line where invoice_id in (select id from invoice where student_current_account_id = ?)", accountId);
            jdbc.update("delete from invoice where student_current_account_id = ?", accountId);
            jdbc.update("delete from student_current_account where id = ?", accountId);
        });
        createdAccountIds.clear();
        String people = director.getId() + "," + administrative.getId();
        jdbc.update("delete from authorization_request_status_change where authorization_request_id in (select id from authorization_request where requested_by_id in (" + people + "))");
        jdbc.update("delete from authorization_request where requested_by_id in (" + people + ")");
    }

    private Person persistPerson(String prefix, String roleName) {
        return transactionTemplate.execute(status -> {
            Person person = new Person();
            String username = prefix + "." + System.nanoTime();
            person.setUsername(username);
            person.setDni(Long.toString(Math.abs((long) username.hashCode())));
            person.setName("Persona");
            person.setLastname("Prueba");
            person.setEmail(username + "@example.com");
            person.setAddress(addressRepository.save(new Address()));
            person.setStatus(StatusType.ACTIVE);
            person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
            return personRepository.save(person);
        });
    }

    private void authenticateAs(Person person) {
        String authority = "ROLE_" + person.getRoles().iterator().next().getName();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(person.getId().toString(), null, authority));
    }

    private void today(LocalDate date) {
        MutableClock.INSTANCE.setDate(date);
    }

    private EnrollmentReference enrollment() {
        return EnrollmentReference.ofProgram(learner.programEnrollmentId());
    }

    private StudentDiscountGrantRequest scholarship(String percent, YearMonth from, YearMonth to) {
        return new StudentDiscountGrantRequest(DiscountType.SCHOLARSHIP, null, new MonetaryAdjustment.Proportional(Percentage.ofPoints(new BigDecimal(percent))),
                InvoiceType.MONTHLY_INSTALLMENT, from, to, "Situación económica");
    }

    private StudentDiscountGrantRequest individual(InvoiceType invoiceType, MonetaryAdjustment calculation, YearMonth from, YearMonth to, String reason) {
        return new StudentDiscountGrantRequest(DiscountType.INDIVIDUAL, null, calculation, invoiceType, from, to, reason);
    }

    private MonetaryAdjustment percent(String points) {
        return new MonetaryAdjustment.Proportional(Percentage.ofPoints(new BigDecimal(points)));
    }

    private BillingOperationOutcome<StudentDiscount> grant(Person actor, StudentDiscountGrantRequest request) {
        authenticateAs(actor);
        boolean byDirector = actor.getId().equals(director.getId());
        return pipeline.runOperation(grantOperation, GrantStudentDiscountCommand.byPerson(enrollment(), request, actor.getId(), byDirector));
    }

    private StudentDiscount grantAsDirector(StudentDiscountGrantRequest request) {
        return ((BillingOperationOutcome.Processed<StudentDiscount>) grant(director, request)).result();
    }

    private Long proposeGrant(StudentDiscountGrantRequest request) {
        return ((BillingOperationOutcome.AwaitingAuthorization<StudentDiscount>) grant(administrative, request)).authorizationRequestId();
    }

    private BillingOperationOutcome<StudentDiscount> revoke(Person actor, Long studentDiscountId, String reason) {
        authenticateAs(actor);
        boolean byDirector = actor.getId().equals(director.getId());
        return pipeline.runOperation(revokeOperation, new RevokeStudentDiscountCommand(studentDiscountId, reason, actor.getId(), byDirector, LocalDate.now(MutableClock.INSTANCE)));
    }

    private void emitInstallment(YearMonth period) {
        issuingService.issueInvoiceInNewTransaction(new InvoiceIssuingRequest(learner.studentId(), learner.programEnrollmentId(), null, offering.programId(), null, null,
                FeeType.MONTHLY_INSTALLMENT, period, period.atDay(11), period.atDay(1), InvoiceIssuingReason.MONTHLY_RUN, learner.selectionId(), null, null, false, null,
                null, null, null, null, ACTOR));
    }

    private void useCohortLearner() {
        Long templateId = factory.newTemplate(offering, 2027);
        Long cohortId = factory.newCohort(templateId, CohortIntake.FIRST, LocalDate.of(2027, 3, 1), Percentage.ofPoints(BigDecimal.ZERO));
        learner = factory.newLearner(offering, cohortId);
    }

    private Long newCourseEnrollment() {
        Person alumni = persistPerson("sd.course.student", "ALUMNO");
        return transactionTemplate.execute(status -> {
            Institution institution = new Institution();
            institution.setName("Instituto " + System.nanoTime());
            entityManager.persist(institution);
            Student student = new Student();
            student.setPerson(alumni);
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            Term term = new Term();
            term.setYear(2027);
            term.setStatus(StatusType.ACTIVE);
            term.setStartDate(LocalDateTime.of(2027, 3, 1, 0, 0));
            term.setEndDate(LocalDateTime.of(2027, 9, 30, 23, 59));
            entityManager.persist(term);
            Course course = new Course();
            course.setName("Curso " + System.nanoTime());
            course.setTerm(term);
            course.setDurationMonths(7);
            course.setIsActive(StatusType.ACTIVE);
            entityManager.persist(course);
            CourseEnrollment enrollment = new CourseEnrollment();
            enrollment.setStudent(student);
            enrollment.setCourse(course);
            enrollment.setStatus(EnrollmentStatus.ENROLLED);
            enrollment.setEnrollmentDate(LocalDateTime.of(2027, 2, 1, 10, 0));
            entityManager.persist(enrollment);
            courseStudentId = student.getId();
            return enrollment.getId();
        });
    }

    private Long insertLiveLine(Long studentId, FeeType feeType, YearMonth period, Long programEnrollmentId, Long courseEnrollmentId) {
        List<Long> existing = jdbc.queryForList("select id from student_current_account where student_id = ? and currency = 'ARS'", Long.class, studentId);
        Long accountId;
        if (existing.isEmpty()) {
            accountId = jdbc.queryForObject("""
                    insert into student_current_account (student_id, currency, balance_amount, balance_amount_currency, balance_side,
                        unallocated_credit_amount, unallocated_credit_amount_currency, opened_at)
                    values (?, 'ARS', 0, 'ARS', 'ZERO', 0, 'ARS', now()) returning id
                    """, Long.class, studentId);
            createdAccountIds.add(accountId);
        } else {
            accountId = existing.get(0);
        }
        Long invoiceId = jdbc.queryForObject("""
                insert into invoice (student_current_account_id, currency, document_type, invoice_type, issuing_reason, document_status,
                    lines_total, lines_total_currency, discounts_total, discounts_total_currency, total, total_currency, paid_amount, paid_amount_currency,
                    credited_amount, credited_amount_currency, outstanding_amount, outstanding_amount_currency, created_at, updated_at)
                values (?, 'ARS', 'INVOICE', 'MONTHLY_INSTALLMENT', 'MONTHLY_RUN', 'DRAFT', 100, 'ARS', 0, 'ARS', 100, 'ARS', 0, 'ARS', 0, 'ARS', 100, 'ARS', now(), now())
                returning id
                """, Long.class, accountId);
        return jdbc.queryForObject("""
                insert into invoice_line (invoice_id, currency, line_number, fee_type, concept, period, charge_key, amount, amount_currency, discount_amount,
                    discount_amount_currency, credited_amount, credited_amount_currency, free_credited_amount, free_credited_amount_currency,
                    program_enrollment_id, course_enrollment_id, price_version_id)
                values (?, 'ARS', 1, ?, 'Cargo', ?, ?, 100, 'ARS', 0, 'ARS', 0, 'ARS', 0, 'ARS', ?, ?, (select min(id) from price_version)) returning id
                """, Long.class, invoiceId, feeType.name(), java.sql.Date.valueOf(period.atDay(1)), "K" + System.nanoTime(), programEnrollmentId, courseEnrollmentId);
    }

    private Long newTemplateWithTerm(int year, LocalDate start, LocalDate end) {
        Long templateId = factory.newTemplate(offering, year);
        jdbc.update("update term set start_date = ?, end_date = ? where id = (select term_id from program_year_billing_template where id = ?)",
                java.sql.Timestamp.valueOf(start.atStartOfDay()), java.sql.Timestamp.valueOf(end.atTime(23, 59)), templateId);
        return templateId;
    }

    private Long newCohortLearnerOn(Long templateId, Long currentTemplateId) {
        Long cohortId = factory.newCohort(templateId, CohortIntake.FIRST, LocalDate.of(2027, 3, 1), Percentage.ofPoints(BigDecimal.ZERO));
        learner = factory.newLearner(offering, cohortId);
        if (currentTemplateId != null) {
            jdbc.update("update program_enrollment set current_billing_template_id = ? where id = ?", currentTemplateId, learner.programEnrollmentId());
        }
        return learner.studentId();
    }

    private void deleteDiscountsOfStudent(Long studentId) {
        jdbc.execute("alter table student_discount_status_change disable trigger trg_student_discount_status_change_append_only");
        jdbc.execute("alter table invoice_discount disable trigger trg_invoice_discount_immutable_when_issued");
        jdbc.update("delete from invoice_discount where student_discount_id in (select id from student_discount where student_id = ?)", studentId);
        jdbc.update("delete from student_discount_status_change where student_discount_id in (select id from student_discount where student_id = ?)", studentId);
        jdbc.update("delete from student_discount where student_id = ?", studentId);
        jdbc.execute("alter table invoice_discount enable trigger trg_invoice_discount_immutable_when_issued");
        jdbc.execute("alter table student_discount_status_change enable trigger trg_student_discount_status_change_append_only");
    }

    private void deleteDiscountsOfCourse(Long courseEnrollmentId) {
        jdbc.execute("alter table student_discount_status_change disable trigger trg_student_discount_status_change_append_only");
        jdbc.update("delete from student_discount_status_change where student_discount_id in (select id from student_discount where course_enrollment_id = ?)", courseEnrollmentId);
        jdbc.update("delete from student_discount where course_enrollment_id = ?", courseEnrollmentId);
        jdbc.execute("alter table student_discount_status_change enable trigger trg_student_discount_status_change_append_only");
    }

    private void releaseChargeKey(Long lineId) {
        jdbc.update("update invoice_line set charge_key_released_at = now() where id = ?", lineId);
    }

    private StudentDiscount grantOnCourse(Long courseEnrollmentId, StudentDiscountGrantRequest request) {
        authenticateAs(director);
        return ((BillingOperationOutcome.Processed<StudentDiscount>) pipeline.runOperation(grantOperation,
                GrantStudentDiscountCommand.byPerson(EnrollmentReference.ofCourse(courseEnrollmentId), request, director.getId(), true))).result();
    }

    private long discountsOfLearner() {
        return jdbc.queryForObject("select count(*) from student_discount where student_id = ?", Long.class, learner.studentId());
    }

    private record PreviewOutcome(StudentDiscountGrantPreview preview, List<String> rejectionCodes, BillingOperationOutcome<StudentDiscount> outcome) {
    }

    private PreviewOutcome previewThenGrant(Person actor, EnrollmentReference target, StudentDiscountGrantRequest request) {
        authenticateAs(actor);
        boolean byDirector = actor.getId().equals(director.getId());
        StudentDiscountGrantPreview preview = transactionTemplate.execute(status -> grantOperation.previewGrant(GrantStudentDiscountCommand.byPerson(target, request, actor.getId(), byDirector)));
        try {
            return new PreviewOutcome(preview, List.of(), pipeline.runOperation(grantOperation, GrantStudentDiscountCommand.byPerson(target, request, actor.getId(), byDirector)));
        } catch (RuntimeException rejection) {
            assertThat(rejection).isInstanceOf(ReportsPolicyViolations.class);
            return new PreviewOutcome(preview, ((ReportsPolicyViolations) rejection).violations().stream().map(violation -> violation.code()).toList(), null);
        }
    }

    private List<String> codesOf(StudentDiscountGrantPreview preview) {
        return preview.violations().stream().map(violation -> violation.code()).toList();
    }

    private long countOf(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    private StudentDiscountGrantRequest withDefinition(StudentDiscountGrantRequest request, Long definitionId) {
        return new StudentDiscountGrantRequest(request.discountType(), definitionId, request.calculation(), request.invoiceType(), request.fromPeriod(), request.toPeriod(), request.reason());
    }

    @Test
    void anAllowedPreviewIsFollowedByARealGrantTheDirectorPublishesAndTheAdministrativeProposes() {
        PreviewOutcome byDirector = previewThenGrant(director, enrollment(), scholarship("50", YearMonth.of(2027, 4), YearMonth.of(2027, 6)));
        PreviewOutcome byAdministrative = previewThenGrant(administrative, enrollment(), individual(InvoiceType.MONTHLY_INSTALLMENT, percent("10"), YearMonth.of(2027, 8), YearMonth.of(2027, 8), "Hermano"));

        assertThat(byDirector.preview().allowed()).isTrue();
        assertThat(byDirector.outcome()).isInstanceOf(BillingOperationOutcome.Processed.class);
        assertThat(byAdministrative.preview().allowed()).isTrue();
        assertThat(byAdministrative.preview().requiresDirectorApproval()).isTrue();
        assertThat(byAdministrative.outcome()).isInstanceOf(BillingOperationOutcome.AwaitingAuthorization.class);
    }

    @Test
    void aRejectedPreviewIsFollowedByARealRejectionWithTheSameViolationCodes() {
        grantAsDirector(scholarship("50", YearMonth.of(2027, 4), YearMonth.of(2027, 6)));

        PreviewOutcome overlapping = previewThenGrant(director, enrollment(), scholarship("30", YearMonth.of(2027, 5), YearMonth.of(2027, 8)));
        PreviewOutcome early = previewThenGrant(director, enrollment(), scholarship("30", YearMonth.of(2027, 2), YearMonth.of(2027, 8)));

        assertThat(overlapping.preview().allowed()).isFalse();
        assertThat(overlapping.rejectionCodes()).isNotEmpty().isEqualTo(codesOf(overlapping.preview()));
        assertThat(early.preview().allowed()).isFalse();
        assertThat(early.rejectionCodes()).isNotEmpty().isEqualTo(codesOf(early.preview()));
    }

    @Test
    void thePreviewOfTheAdministrativeCreatesNoAuthorizationRequestNorAnyRow() {
        StudentDiscountGrantRequest request = individual(InvoiceType.MONTHLY_INSTALLMENT, percent("10"), YearMonth.of(2027, 8), YearMonth.of(2027, 8), "Hermano");
        authenticateAs(administrative);
        List<String> tables = List.of("authorization_request", "authorization_request_status_change", "student_discount", "student_discount_status_change", "notification_delivery");
        List<Long> before = tables.stream().map(this::countOf).toList();

        StudentDiscountGrantPreview preview = transactionTemplate.execute(status -> grantOperation.previewGrant(
                GrantStudentDiscountCommand.byPerson(enrollment(), request, administrative.getId(), false)));

        assertThat(preview.allowed()).isTrue();
        assertThat(tables.stream().map(this::countOf).toList()).isEqualTo(before);
        proposeGrant(request);
        assertThat(countOf("authorization_request")).isGreaterThan(before.get(0));
    }

    @Test
    void aScholarshipOverARealAnnualUpfrontSelectionIsRejectedByThePreviewAndByTheGrant() {
        jdbc.update("update payment_plan_option set cadence = 'ANNUAL_UPFRONT' where id = (select payment_plan_option_id from payment_plan_selection where id = ?)", learner.selectionId());

        PreviewOutcome outcome = previewThenGrant(director, enrollment(), scholarship("50", YearMonth.of(2027, 4), YearMonth.of(2027, 6)));

        assertThat(outcome.preview().annualUpfrontCoversRange()).isTrue();
        assertThat(codesOf(outcome.preview())).contains("SCHOLARSHIP_NOT_AVAILABLE_WITH_ANNUAL_UPFRONT");
        assertThat(outcome.rejectionCodes()).isEqualTo(codesOf(outcome.preview()));
    }

    @Test
    void thePreviewAndTheGrantAgreeOnACourseEnrollmentWithTheTermEndAsDefaultEnd() {
        Long courseEnrollmentId = newCourseEnrollment();
        EnrollmentReference course = EnrollmentReference.ofCourse(courseEnrollmentId);

        PreviewOutcome allowed = previewThenGrant(director, course, scholarship("50", YearMonth.of(2027, 4), null));
        PreviewOutcome rejected = previewThenGrant(director, course, scholarship("20", YearMonth.of(2027, 6), YearMonth.of(2027, 7)));

        assertThat(allowed.preview().allowed()).isTrue();
        assertThat(allowed.preview().toPeriod()).isEqualTo(YearMonth.of(2027, 9));
        assertThat(allowed.preview().coveredPeriods()).isEqualTo(6);
        assertThat(((BillingOperationOutcome.Processed<StudentDiscount>) allowed.outcome()).result().getToPeriod()).isEqualTo(YearMonth.of(2027, 9));
        assertThat(rejected.preview().allowed()).isFalse();
        assertThat(rejected.rejectionCodes()).isNotEmpty().isEqualTo(codesOf(rejected.preview()));
        deleteDiscountsOfCourse(courseEnrollmentId);
    }

    @Test
    void aClosedDefinitionAndANonScholarshipDefinitionAreRejectedByThePreviewAndByTheGrant() {
        authenticateAs(director);
        DiscountDefinition annual = ((BillingOperationOutcome.Processed<DiscountDefinition>) pipeline.runOperation(createDefinitionOperation,
                CreateDiscountDefinitionCommand.byPerson(DiscountDraftBuilder.annualUpfront().named("Pago anual para paridad").withPercent("15").build(), director.getId(), true))).result();
        DiscountDefinition scholarshipDefinition = ((BillingOperationOutcome.Processed<DiscountDefinition>) pipeline.runOperation(createDefinitionOperation,
                CreateDiscountDefinitionCommand.byPerson(DiscountDraftBuilder.scholarship().named("Beca para paridad").withPercent("15").build(), director.getId(), true))).result();
        pipeline.runOperation(closeDefinitionOperation, new CloseDiscountDefinitionCommand(scholarshipDefinition.getId(), "Fin de campaña", director.getId(), true));

        PreviewOutcome notScholarship = previewThenGrant(director, enrollment(), withDefinition(scholarship("50", YearMonth.of(2027, 4), YearMonth.of(2027, 6)), annual.getId()));
        PreviewOutcome closed = previewThenGrant(director, enrollment(), withDefinition(scholarship("50", YearMonth.of(2027, 4), YearMonth.of(2027, 6)), scholarshipDefinition.getId()));

        assertThat(codesOf(notScholarship.preview())).containsExactly("INVALID_STUDENT_DISCOUNT");
        assertThat(notScholarship.rejectionCodes()).isEqualTo(codesOf(notScholarship.preview()));
        assertThat(codesOf(closed.preview())).containsExactly("DISCOUNT_DEFINITION_CLOSED");
        assertThat(closed.rejectionCodes()).isEqualTo(codesOf(closed.preview()));
    }

    @Test
    void aScholarshipCannotStartBeforeTheNextInstallmentAndNeverCreatesACreditNote() {
        today(LocalDate.of(2027, 3, 1));
        emitInstallment(YearMonth.of(2027, 3));
        today(TODAY);
        long creditNotesBefore = jdbc.queryForObject("select count(*) from credit_note", Long.class);

        assertThatThrownBy(() -> grant(director, scholarship("50", YearMonth.of(2027, 3), YearMonth.of(2027, 6)))).isInstanceOf(ScholarshipStartBeforeNextInstallmentException.class);
        assertThat(discountsOfLearner()).isZero();

        StudentDiscount aprilScholarship = grantAsDirector(scholarship("50", YearMonth.of(2027, 4), YearMonth.of(2027, 6)));

        assertThat(aprilScholarship.getStatus()).isEqualTo(StudentDiscountStatus.ACTIVE);
        assertThat(jdbc.queryForObject("select count(*) from credit_note", Long.class)).isEqualTo(creditNotesBefore);
        assertThat(jdbc.queryForObject("""
                select coalesce(sum(l.discount_amount), 0) from invoice_line l where l.program_enrollment_id = ?
                """, BigDecimal.class, learner.programEnrollmentId()).signum()).isZero();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void theNextInstallmentIsTheFirstMissingMonthEvenIfALaterOneIsEmitted() {
        today(LocalDate.of(2027, 4, 1));
        emitInstallment(YearMonth.of(2027, 4));
        today(TODAY);

        assertThat(grantAsDirector(scholarship("20", YearMonth.of(2027, 3), YearMonth.of(2027, 6))).getFromPeriod()).isEqualTo(YearMonth.of(2027, 3));
    }

    @Test
    void anAdministrativeProposalExpiresOnTheFirstDayOfTheStartMonthAndCannotBeApprovedAfterwards() {
        today(LocalDate.of(2027, 2, 25));
        Long requestId = proposeGrant(scholarship("25", YearMonth.of(2027, 3), YearMonth.of(2027, 6)));
        assertThat(discountsOfLearner()).isZero();

        today(LocalDate.of(2027, 3, 1));
        expirer.expireDueRequests();
        today(LocalDate.of(2027, 3, 5));
        authenticateAs(director);

        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus()).isEqualTo(AuthorizationRequestStatus.EXPIRED);
        assertThatThrownBy(() -> approveRequest.approve(requestId, "tarde")).isInstanceOf(IllegalAuthorizationRequestTransitionException.class);
        assertThat(discountsOfLearner()).isZero();
    }

    @Test
    void aProposalIsRevalidatedWhenTheDirectorApprovesIt() {
        today(LocalDate.of(2027, 3, 20));
        Long requestId = proposeGrant(scholarship("30", YearMonth.of(2027, 5), YearMonth.of(2027, 8)));

        today(LocalDate.of(2027, 4, 2));
        authenticateAs(director);
        approveRequest.approve(requestId, "ok");

        assertThat(discountsOfLearner()).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select status from student_discount where student_id = ?", String.class, learner.studentId())).isEqualTo("ACTIVE");
    }

    @Test
    void anApprovalAfterTheStartMonthWasEmittedIsRejectedByTheRevalidation() {
        today(LocalDate.of(2027, 3, 10));
        Long requestId = proposeGrant(scholarship("30", YearMonth.of(2027, 4), YearMonth.of(2027, 8)));
        emitInstallment(YearMonth.of(2027, 3));
        emitInstallment(YearMonth.of(2027, 4));

        authenticateAs(director);
        assertThatThrownBy(() -> approveRequest.approve(requestId, "ok")).isInstanceOf(ScholarshipStartBeforeNextInstallmentException.class);
        assertThat(discountsOfLearner()).isZero();
    }

    @Test
    void aProposalWhoseStartMonthAlreadyBeganIsRejectedWhenProposed() {
        today(LocalDate.of(2027, 3, 10));
        long requestsBefore = jdbc.queryForObject("select count(*) from authorization_request where requested_by_id = ?", Long.class, administrative.getId());

        assertThatThrownBy(() -> grant(administrative, scholarship("25", YearMonth.of(2027, 3), YearMonth.of(2027, 6)))).isInstanceOf(StudentDiscountProposalExpiredException.class);
        grant(administrative, scholarship("25", YearMonth.of(2027, 4), YearMonth.of(2027, 6)));

        assertThat(jdbc.queryForObject("select count(*) from authorization_request where requested_by_id = ?", Long.class, administrative.getId())).isEqualTo(requestsBefore + 1);
        grantAsDirector(scholarship("25", YearMonth.of(2027, 3), YearMonth.of(2027, 6)));
    }

    @Test
    void approvingOnTheDeadlineDayIsRejectedEvenBeforeTheExpirationJobRuns() {
        today(LocalDate.of(2027, 2, 25));
        Long requestId = proposeGrant(scholarship("25", YearMonth.of(2027, 3), YearMonth.of(2027, 6)));

        today(LocalDate.of(2027, 3, 1));
        authenticateAs(director);

        assertThatThrownBy(() -> approveRequest.approve(requestId, "tarde")).isInstanceOf(StudentDiscountProposalExpiredException.class);
        assertThat(discountsOfLearner()).isZero();
    }

    @Test
    void approvingTheDayBeforeTheDeadlineStillGrantsTheDiscount() {
        today(LocalDate.of(2027, 2, 25));
        Long requestId = proposeGrant(scholarship("25", YearMonth.of(2027, 3), YearMonth.of(2027, 6)));

        today(LocalDate.of(2027, 2, 28));
        authenticateAs(director);
        approveRequest.approve(requestId, "a tiempo");

        assertThat(discountsOfLearner()).isEqualTo(1L);
    }

    @Test
    void aMonthWhoseChargeKeyWasReleasedDoesNotCountAsEmitted() {
        today(LocalDate.of(2027, 3, 1));
        emitInstallment(YearMonth.of(2027, 3));
        today(TODAY);
        assertThatThrownBy(() -> grant(director, scholarship("50", YearMonth.of(2027, 3), YearMonth.of(2027, 6)))).isInstanceOf(ScholarshipStartBeforeNextInstallmentException.class);

        jdbc.update("update invoice_line set charge_key_released_at = now() where program_enrollment_id = ?", learner.programEnrollmentId());

        assertThat(grantAsDirector(scholarship("50", YearMonth.of(2027, 3), YearMonth.of(2027, 6))).getFromPeriod()).isEqualTo(YearMonth.of(2027, 3));
    }

    @Test
    void theAdministrativeGrantIsPendingUntilTheDirectorApproves() {
        Long requestId = proposeGrant(scholarship("25", YearMonth.of(2027, 4), YearMonth.of(2027, 8)));

        assertThat(discountsOfLearner()).isZero();
        authenticateAs(director);
        approveRequest.approve(requestId, "de acuerdo");

        var row = jdbc.queryForMap("select status, granted_by_id, authorization_request_id from student_discount where student_id = ?", learner.studentId());
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("granted_by_id")).isEqualTo(administrative.getId());
        assertThat(row.get("authorization_request_id")).isEqualTo(requestId);
        assertThat(jdbc.queryForObject("select count(*) from student_discount_status_change where event = 'DISCOUNT_GRANTED' and to_status = 'ACTIVE' and student_discount_id in (select id from student_discount where student_id = ?)",
                Long.class, learner.studentId())).isEqualTo(1L);
    }

    @Test
    void aRevocationAppliesFromTheNextInstallmentToEmitAndKeepsEmittedMonths() {
        StudentDiscount scholarship = grantAsDirector(scholarship("50", YearMonth.of(2027, 3), YearMonth.of(2027, 12)));
        today(LocalDate.of(2027, 6, 1));
        emitInstallment(YearMonth.of(2027, 6));
        today(LocalDate.of(2027, 6, 10));

        StudentDiscount revoked = ((BillingOperationOutcome.Processed<StudentDiscount>) revoke(director, scholarship.getId(), "Cambió su situación")).result();

        assertThat(revoked.getStatus()).isEqualTo(StudentDiscountStatus.REVOKED);
        assertThat(revoked.getRevokedFromPeriod()).isEqualTo(YearMonth.of(2027, 7));
        assertThat(revoked.coversPeriod(YearMonth.of(2027, 6))).isTrue();
        assertThat(revoked.coversPeriod(YearMonth.of(2027, 7))).isFalse();
    }

    @Test
    void aRevocationWithoutReasonIsRejectedAndAnEndedStateIsTerminal() {
        StudentDiscount scholarship = grantAsDirector(scholarship("50", YearMonth.of(2027, 4), YearMonth.of(2027, 8)));

        assertThatThrownBy(() -> revoke(director, scholarship.getId(), " ")).isInstanceOf(RevocationReasonRequiredException.class);
        assertThatThrownBy(() -> revoke(director, scholarship.getId(), "x".repeat(401))).isInstanceOf(RevocationReasonRequiredException.class);
        revoke(director, scholarship.getId(), "x".repeat(400));
        assertThatThrownBy(() -> revoke(director, scholarship.getId(), "otra vez")).isInstanceOf(IllegalStudentDiscountTransitionException.class);
    }

    @Test
    void aRevokedScholarshipAndANewOneFromTheSameMonthCoexist() {
        StudentDiscount fifty = grantAsDirector(scholarship("50", YearMonth.of(2027, 6), YearMonth.of(2027, 12)));
        revoke(director, fifty.getId(), "Reemplazo");
        StudentDiscount twentyFive = grantAsDirector(scholarship("25", YearMonth.of(2027, 6), YearMonth.of(2027, 12)));

        assertThat(discountsOfLearner()).isEqualTo(2L);
        assertThat(repository.findStatusById(fifty.getId())).contains(StudentDiscountStatus.REVOKED);
        assertThat(repository.findStatusById(twentyFive.getId())).contains(StudentDiscountStatus.ACTIVE);
    }

    @Test
    void anAdministrativeRevocationProposalExpiresAtTheEndOfTheMonthAndTheScholarshipStaysActive() {
        StudentDiscount scholarship = grantAsDirector(scholarship("50", YearMonth.of(2027, 4), YearMonth.of(2027, 12)));
        today(LocalDate.of(2027, 3, 10));
        Long requestId = ((BillingOperationOutcome.AwaitingAuthorization<StudentDiscount>) revoke(administrative, scholarship.getId(), "Corresponde revocar")).authorizationRequestId();

        today(LocalDate.of(2027, 3, 31));
        expirer.expireDueRequests();
        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus()).isEqualTo(AuthorizationRequestStatus.PENDING);

        today(LocalDate.of(2027, 4, 1));
        expirer.expireDueRequests();
        authenticateAs(director);

        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus()).isEqualTo(AuthorizationRequestStatus.EXPIRED);
        assertThatThrownBy(() -> approveRequest.approve(requestId, "tarde")).isInstanceOf(IllegalAuthorizationRequestTransitionException.class);
        assertThat(repository.findStatusById(scholarship.getId())).contains(StudentDiscountStatus.ACTIVE);
    }

    @Test
    void anApprovalOfARevocationAfterTheEndOfTheMonthIsRejectedEvenBeforeTheExpirationJobRuns() {
        StudentDiscount scholarship = grantAsDirector(scholarship("50", YearMonth.of(2027, 4), YearMonth.of(2027, 12)));
        today(LocalDate.of(2027, 3, 10));
        Long requestId = ((BillingOperationOutcome.AwaitingAuthorization<StudentDiscount>) revoke(administrative, scholarship.getId(), "Corresponde revocar")).authorizationRequestId();

        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 4, 1, 0, 5));
        authenticateAs(director);

        assertThatThrownBy(() -> approveRequest.approve(requestId, "tarde")).isInstanceOf(StudentDiscountProposalExpiredException.class);
        assertThat(repository.findStatusById(scholarship.getId())).contains(StudentDiscountStatus.ACTIVE);
    }

    @Test
    void anApprovalOfARevocationOnTheLastMinuteOfTheMonthStillRevokes() {
        StudentDiscount scholarship = grantAsDirector(scholarship("50", YearMonth.of(2027, 4), YearMonth.of(2027, 12)));
        today(LocalDate.of(2027, 3, 10));
        Long requestId = ((BillingOperationOutcome.AwaitingAuthorization<StudentDiscount>) revoke(administrative, scholarship.getId(), "Corresponde revocar")).authorizationRequestId();

        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 31, 23, 59));
        authenticateAs(director);
        approveRequest.approve(requestId, "a tiempo");

        assertThat(repository.findStatusById(scholarship.getId())).contains(StudentDiscountStatus.REVOKED);
    }

    @Test
    void aScholarshipWithoutAnEndPeriodRunsUntilTheLastMonthOfTheLectiveYear() {
        useCohortLearner();

        StudentDiscount scholarship = grantAsDirector(scholarship("50", YearMonth.of(2027, 4), null));
        StudentDiscount individualOnEnrollmentFee = grantAsDirector(individual(InvoiceType.ENROLLMENT_FEE, percent("30"), YearMonth.of(2027, 4), null, "Hermano"));

        assertThat(scholarship.getToPeriod()).isEqualTo(YearMonth.of(2027, 12));
        assertThat(individualOnEnrollmentFee.getToPeriod()).isEqualTo(YearMonth.of(2027, 4));
    }

    @Test
    void aProposalWithoutAnEndPeriodGetsTheLectiveYearEndWhenTheDirectorApprovesIt() {
        useCohortLearner();
        Long requestId = proposeGrant(scholarship("25", YearMonth.of(2027, 4), null));

        authenticateAs(director);
        approveRequest.approve(requestId, "ok");

        assertThat(jdbc.queryForObject("select to_period from student_discount where student_id = ?", java.sql.Date.class, learner.studentId()).toLocalDate())
                .isEqualTo(LocalDate.of(2027, 12, 1));
    }

    @Test
    void aScholarshipWithoutAnEndPeriodOnAnEnrollmentWithoutLectiveYearIsRejected() {
        assertThatThrownBy(() -> grant(director, scholarship("50", YearMonth.of(2027, 4), null))).isInstanceOf(InvalidScholarshipPeriodException.class);
        assertThat(discountsOfLearner()).isZero();
    }

    @Test
    void aCourseScholarshipWithoutAnEndPeriodRunsUntilTheEndOfTheCourseTerm() {
        Long courseEnrollmentId = newCourseEnrollment();

        StudentDiscount scholarship = grantOnCourse(courseEnrollmentId, scholarship("50", YearMonth.of(2027, 4), null));

        assertThat(scholarship.getToPeriod()).isEqualTo(YearMonth.of(2027, 9));
        deleteDiscountsOfCourse(courseEnrollmentId);
    }

    @Test
    void aReleasedChargeKeyOfACourseEnrollmentDoesNotCountAsAnEmittedInstallment() {
        Long courseEnrollmentId = newCourseEnrollment();
        Long lineId = insertLiveLine(courseStudentId, FeeType.MONTHLY_INSTALLMENT, YearMonth.of(2027, 3), null, courseEnrollmentId);

        assertThatThrownBy(() -> grantOnCourse(courseEnrollmentId, scholarship("50", YearMonth.of(2027, 3), YearMonth.of(2027, 6))))
                .isInstanceOf(ScholarshipStartBeforeNextInstallmentException.class);

        releaseChargeKey(lineId);
        StudentDiscount granted = grantOnCourse(courseEnrollmentId, scholarship("50", YearMonth.of(2027, 3), YearMonth.of(2027, 6)));

        assertThat(granted.getFromPeriod()).isEqualTo(YearMonth.of(2027, 3));
        deleteDiscountsOfCourse(courseEnrollmentId);
    }

    @Test
    void aSecondScholarshipWithAnOverlappingRangeIsRejectedWhenProposedAndNeverCreated() {
        grantAsDirector(scholarship("50", YearMonth.of(2027, 3), YearMonth.of(2027, 12)));

        assertThatThrownBy(() -> grant(director, scholarship("30", YearMonth.of(2027, 6), YearMonth.of(2027, 12)))).isInstanceOf(StudentDiscountOverlapException.class)
                .hasMessageContaining("superpone");
        assertThatThrownBy(() -> grant(administrative, scholarship("30", YearMonth.of(2027, 6), YearMonth.of(2027, 12)))).isInstanceOf(StudentDiscountOverlapException.class);
        assertThat(discountsOfLearner()).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from authorization_request where requested_by_id = ?", Long.class, administrative.getId())).isZero();
    }

    @Test
    void anOverlapOfASingleMonthIsRejectedButAdjacentRangesAreAccepted() {
        grantAsDirector(scholarship("50", YearMonth.of(2027, 5), YearMonth.of(2027, 8)));

        assertThatThrownBy(() -> grant(director, scholarship("30", YearMonth.of(2027, 3), YearMonth.of(2027, 5)))).isInstanceOf(StudentDiscountOverlapException.class);
        assertThatThrownBy(() -> grant(director, scholarship("30", YearMonth.of(2027, 8), YearMonth.of(2027, 10)))).isInstanceOf(StudentDiscountOverlapException.class);
        grantAsDirector(scholarship("30", YearMonth.of(2027, 3), YearMonth.of(2027, 4)));
        grantAsDirector(scholarship("30", YearMonth.of(2027, 9), YearMonth.of(2027, 10)));

        assertThat(discountsOfLearner()).isEqualTo(3L);
    }

    @Test
    void discountsOfAnotherKindOrAnotherInvoiceTypeOrARevokedOneDoNotOverlap() {
        StudentDiscount scholarship = grantAsDirector(scholarship("50", YearMonth.of(2027, 3), YearMonth.of(2027, 12)));

        grantAsDirector(individual(InvoiceType.MONTHLY_INSTALLMENT, percent("10"), YearMonth.of(2027, 4), YearMonth.of(2027, 6), "Individual"));
        grantAsDirector(individual(InvoiceType.ENROLLMENT_FEE, percent("10"), YearMonth.of(2027, 4), YearMonth.of(2027, 4), "Matrícula"));
        assertThatThrownBy(() -> grant(director, individual(InvoiceType.MONTHLY_INSTALLMENT, percent("20"), YearMonth.of(2027, 6), YearMonth.of(2027, 7), "Otro")))
                .isInstanceOf(StudentDiscountOverlapException.class);
        revoke(director, scholarship.getId(), "Corresponde revocar");
        grantAsDirector(scholarship("30", YearMonth.of(2027, 6), YearMonth.of(2027, 12)));

        assertThat(discountsOfLearner()).isEqualTo(4L);
    }

    @Test
    void theOverlapIsCheckedAgainAtApprovalWhenAnotherDiscountWasGrantedMeanwhile() {
        Long requestId = proposeGrant(scholarship("25", YearMonth.of(2027, 4), YearMonth.of(2027, 8)));
        grantAsDirector(scholarship("50", YearMonth.of(2027, 6), YearMonth.of(2027, 10)));
        authenticateAs(director);

        assertThatThrownBy(() -> approveRequest.approve(requestId, "ok")).isInstanceOf(StudentDiscountOverlapException.class);
        assertThat(discountsOfLearner()).isEqualTo(1L);
    }

    @Test
    void anIndividualDiscountOnTheEnrollmentFeeIsRejectedOnceThatLectiveYearFeeIsIssuedAndOnlyForThatYear() {
        useCohortLearner();
        insertLiveLine(learner.studentId(), FeeType.ENROLLMENT_FEE, YearMonth.of(2025, 12), learner.programEnrollmentId(), null);
        StudentDiscountGrantRequest onFee = individual(InvoiceType.ENROLLMENT_FEE, percent("30"), YearMonth.of(2027, 4), YearMonth.of(2027, 4), "Hermano");

        StudentDiscount first = grantAsDirector(onFee);
        assertThat(first.getApplicableInvoiceType()).isEqualTo(InvoiceType.ENROLLMENT_FEE);
        jdbc.update("update student_discount set status = 'ENDED' where id = ?", first.getId());
        Long issuedFee = insertLiveLine(learner.studentId(), FeeType.ENROLLMENT_FEE, YearMonth.of(2027, 2), learner.programEnrollmentId(), null);

        assertThatThrownBy(() -> grant(director, onFee)).isInstanceOf(EnrollmentFeeAlreadyIssuedException.class);
        releaseChargeKey(issuedFee);
        assertThat(grantAsDirector(onFee).getApplicableInvoiceType()).isEqualTo(InvoiceType.ENROLLMENT_FEE);
    }

    @Test
    void twoIndividualDiscountsOnTheEnrollmentFeeOfTheSameLectiveYearOverlapEvenInDifferentMonths() {
        useCohortLearner();

        grantAsDirector(individual(InvoiceType.ENROLLMENT_FEE, percent("30"), YearMonth.of(2027, 4), null, "Hermano"));

        assertThatThrownBy(() -> grant(director, individual(InvoiceType.ENROLLMENT_FEE, percent("10"), YearMonth.of(2027, 5), null, "Otro")))
                .isInstanceOf(StudentDiscountOverlapException.class);
        assertThat(discountsOfLearner()).isEqualTo(1L);
    }

    @Test
    void theLectiveYearIsPickedFromTheCurrentBillingTemplateEvenWhenTheCohortTemplateAlsoCoversThePeriod() {
        Long templateA = newTemplateWithTerm(2027, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 12, 31));
        Long templateB = newTemplateWithTerm(2028, LocalDate.of(2027, 7, 1), LocalDate.of(2028, 6, 30));

        Long studentOnB = newCohortLearnerOn(templateA, templateB);
        YearMonth endForCurrentB = grantAsDirector(scholarship("50", YearMonth.of(2027, 9), null)).getToPeriod();
        deleteDiscountsOfStudent(studentOnB);

        newCohortLearnerOn(templateB, templateA);
        YearMonth endForCurrentA = grantAsDirector(scholarship("50", YearMonth.of(2027, 9), null)).getToPeriod();

        assertThat(endForCurrentB).isEqualTo(YearMonth.of(2028, 6));
        assertThat(endForCurrentA).isEqualTo(YearMonth.of(2027, 12));
    }

    @Test
    void twoSimultaneousOverlappingGrantsLeaveExactlyOneActiveDiscount() throws Exception {
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 6; round++) {
                learner = factory.newLearner(offering, null);
                EnrollmentReference target = enrollment();
                Long studentId = learner.studentId();
                java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
                List<java.util.concurrent.Future<Throwable>> attempts = new ArrayList<>();
                for (String percent : new String[]{"40", "20"}) {
                    attempts.add(executor.submit(() -> {
                        authenticateAs(director);
                        barrier.await();
                        try {
                            pipeline.runOperation(grantOperation, GrantStudentDiscountCommand.byPerson(target, scholarship(percent, YearMonth.of(2027, 4), YearMonth.of(2027, 12)),
                                    director.getId(), true));
                            return null;
                        } catch (Throwable failure) {
                            return failure;
                        } finally {
                            SecurityContextHolder.clearContext();
                        }
                    }));
                }
                List<Throwable> failures = new ArrayList<>();
                for (java.util.concurrent.Future<Throwable> attempt : attempts) {
                    Throwable failure = attempt.get();
                    if (failure != null) {
                        failures.add(failure);
                    }
                }

                assertThat(discountsOfLearner()).as("round " + round).isEqualTo(1L);
                assertThat(failures).as("round " + round).hasSize(1);
                assertThat(failures.get(0)).isInstanceOf(StudentDiscountOverlapException.class);
                deleteDiscountsOfStudent(studentId);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void anEnrollmentFeeDiscountOfAnEarlierMonthIsRejectedWhenALaterOneOfTheSameLectiveYearExists() {
        useCohortLearner();
        grantAsDirector(individual(InvoiceType.ENROLLMENT_FEE, percent("30"), YearMonth.of(2027, 6), null, "Hermano"));

        assertThatThrownBy(() -> grant(director, individual(InvoiceType.ENROLLMENT_FEE, percent("10"), YearMonth.of(2027, 4), null, "Otro")))
                .isInstanceOf(StudentDiscountOverlapException.class);
        assertThat(discountsOfLearner()).isEqualTo(1L);
    }

    @Test
    void explicitSingleMonthEnrollmentFeeDiscountsOfTheSameLectiveYearOverlap() {
        useCohortLearner();
        grantAsDirector(individual(InvoiceType.ENROLLMENT_FEE, percent("30"), YearMonth.of(2027, 4), YearMonth.of(2027, 4), "Hermano"));

        assertThatThrownBy(() -> grant(director, individual(InvoiceType.ENROLLMENT_FEE, percent("10"), YearMonth.of(2027, 7), YearMonth.of(2027, 7), "Otro")))
                .isInstanceOf(StudentDiscountOverlapException.class);
        assertThat(discountsOfLearner()).isEqualTo(1L);
    }

    @Test
    void anAdministrativeEnrollmentFeeProposalIsRejectedAtApprovalWhenTheDirectorGrantedAnotherOfTheSameLectiveYearMeanwhile() {
        useCohortLearner();
        Long requestId = proposeGrant(individual(InvoiceType.ENROLLMENT_FEE, percent("10"), YearMonth.of(2027, 4), null, "Propuesta"));
        grantAsDirector(individual(InvoiceType.ENROLLMENT_FEE, percent("30"), YearMonth.of(2027, 8), null, "Director"));
        authenticateAs(director);

        assertThatThrownBy(() -> approveRequest.approve(requestId, "ok")).isInstanceOf(StudentDiscountOverlapException.class);
        assertThat(discountsOfLearner()).isEqualTo(1L);
    }

    @Test
    void anIndividualDiscountOnTheEnrollmentFeeOfACourseIsRejectedOnceItIsIssued() {
        Long courseEnrollmentId = newCourseEnrollment();
        StudentDiscountGrantRequest onFee = individual(InvoiceType.ENROLLMENT_FEE, percent("30"), YearMonth.of(2027, 4), YearMonth.of(2027, 4), "Hermano");
        Long feeLine = insertLiveLine(courseStudentId, FeeType.ENROLLMENT_FEE, YearMonth.of(2027, 3), null, courseEnrollmentId);

        assertThatThrownBy(() -> grantOnCourse(courseEnrollmentId, onFee)).isInstanceOf(EnrollmentFeeAlreadyIssuedException.class);
        releaseChargeKey(feeLine);
        assertThat(grantOnCourse(courseEnrollmentId, onFee).getApplicableInvoiceType()).isEqualTo(InvoiceType.ENROLLMENT_FEE);
        deleteDiscountsOfCourse(courseEnrollmentId);
    }

    @Test
    void aScholarshipOnTheEnrollmentFeeIsRejectedButAnIndividualDiscountOnItIsAccepted() {
        StudentDiscountGrantRequest scholarshipOnFee = new StudentDiscountGrantRequest(DiscountType.SCHOLARSHIP, null, percent("50"), InvoiceType.ENROLLMENT_FEE,
                YearMonth.of(2027, 4), YearMonth.of(2027, 4), "x");
        assertThatThrownBy(() -> grant(director, scholarshipOnFee)).isInstanceOf(DiscountInvoiceTypeMismatchException.class);
        assertThat(discountsOfLearner()).isZero();

        StudentDiscount individual = grantAsDirector(individual(InvoiceType.ENROLLMENT_FEE, percent("30"), YearMonth.of(2027, 4), YearMonth.of(2027, 4), "Hermano de alumno"));

        assertThat(individual.getDiscountType()).isEqualTo(DiscountType.INDIVIDUAL);
        assertThat(individual.getDiscountDefinitionId()).isNull();
        assertThat(individual.getApplicableInvoiceType()).isEqualTo(InvoiceType.ENROLLMENT_FEE);
    }

    @Test
    void anIndividualDiscountNeverTargetsALateFeeNorSpansSeveralMonthsOnASingleCharge() {
        assertThatThrownBy(() -> grant(director, individual(InvoiceType.LATE_FEE, percent("10"), YearMonth.of(2027, 4), YearMonth.of(2027, 4), "x")))
                .isInstanceOf(DiscountInvoiceTypeMismatchException.class);
        assertThatThrownBy(() -> grant(director, individual(InvoiceType.ENROLLMENT_FEE, percent("10"), YearMonth.of(2027, 4), YearMonth.of(2027, 6), "x")))
                .isInstanceOf(InvalidScholarshipPeriodException.class);
        assertThat(discountsOfLearner()).isZero();
    }

    @Test
    void aFixedAmountInAnotherCurrencyIsRejectedAndNeverConverted() {
        MonetaryAdjustment usdAmount = new MonetaryAdjustment.Nominal(Money.of(new BigDecimal("10000.00"), Currency.USD));

        assertThatThrownBy(() -> grant(director, individual(InvoiceType.MONTHLY_INSTALLMENT, usdAmount, YearMonth.of(2027, 4), YearMonth.of(2027, 6), "x")))
                .isInstanceOf(CurrencyMismatchException.class);
        assertThat(discountsOfLearner()).isZero();
    }

    @Test
    void theReasonAndRangeLimitsAreEnforcedBeforeTheDatabaseSeesThem() {
        assertThatThrownBy(() -> grant(director, individual(InvoiceType.MONTHLY_INSTALLMENT, percent("10"), YearMonth.of(2027, 4), YearMonth.of(2027, 6), "x".repeat(401))))
                .isInstanceOf(InvalidStudentDiscountException.class);
        assertThatThrownBy(() -> grant(director, individual(InvoiceType.MONTHLY_INSTALLMENT, percent("10"), YearMonth.of(2027, 6), YearMonth.of(2027, 4), "x")))
                .isInstanceOf(InvalidScholarshipPeriodException.class);
        assertThatThrownBy(() -> grant(director, individual(InvoiceType.MONTHLY_INSTALLMENT, percent("0"), YearMonth.of(2027, 4), YearMonth.of(2027, 6), "x")))
                .isInstanceOf(InvalidStudentDiscountException.class);

        Long requestId = proposeGrant(individual(InvoiceType.MONTHLY_INSTALLMENT, percent("10"), YearMonth.of(2027, 4), YearMonth.of(2027, 6), "x".repeat(400)));
        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus()).isEqualTo(AuthorizationRequestStatus.PENDING);
        StudentDiscount maximum = grantAsDirector(individual(InvoiceType.MONTHLY_INSTALLMENT, percent("100"), YearMonth.of(2027, 4), YearMonth.of(2027, 4), "y".repeat(400)));
        assertThat(maximum.getReason()).hasSize(400);
    }
}
