package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestStatus;
import com.red_escuela.backend.billing.authorization.domain.IllegalAuthorizationRequestTransitionException;
import com.red_escuela.backend.billing.authorization.repository.AuthorizationRequestRepository;
import com.red_escuela.backend.billing.authorization.service.AuthorizationRequestExpirer;
import com.red_escuela.backend.billing.authorization.usecase.ApproveAuthorizationRequestUseCase;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrantRequest;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountStatus;
import com.red_escuela.backend.billing.discount.exception.CurrencyMismatchException;
import com.red_escuela.backend.billing.discount.exception.DiscountInvoiceTypeMismatchException;
import com.red_escuela.backend.billing.discount.exception.IllegalStudentDiscountTransitionException;
import com.red_escuela.backend.billing.discount.exception.InvalidScholarshipPeriodException;
import com.red_escuela.backend.billing.discount.exception.InvalidStudentDiscountException;
import com.red_escuela.backend.billing.discount.exception.RevocationReasonRequiredException;
import com.red_escuela.backend.billing.discount.exception.ScholarshipStartBeforeNextInstallmentException;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
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
import com.red_escuela.backend.identity.entity.Person;
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
import java.time.YearMonth;

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

    private Person director;
    private Person administrative;
    private IssuingScenarioFactory.Offering offering;
    private IssuingScenarioFactory.Learner learner;
    private BillingInvariantChecker invariants;

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
        jdbc.execute("alter table student_discount_status_change disable trigger trg_student_discount_status_change_append_only");
        jdbc.update("delete from student_discount_status_change where student_discount_id in (select id from student_discount where student_id = ?)", learner.studentId());
        jdbc.update("delete from student_discount where student_id = ?", learner.studentId());
        jdbc.execute("alter table student_discount_status_change enable trigger trg_student_discount_status_change_append_only");
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

    private long discountsOfLearner() {
        return jdbc.queryForObject("select count(*) from student_discount where student_id = ?", Long.class, learner.studentId());
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
        today(LocalDate.of(2027, 3, 1));
        Long requestId = proposeGrant(scholarship("30", YearMonth.of(2027, 3), YearMonth.of(2027, 6)));
        emitInstallment(YearMonth.of(2027, 3));

        authenticateAs(director);
        assertThatThrownBy(() -> approveRequest.approve(requestId, "ok")).isInstanceOf(ScholarshipStartBeforeNextInstallmentException.class);
        assertThat(discountsOfLearner()).isZero();
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
