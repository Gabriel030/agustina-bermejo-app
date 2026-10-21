package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.authorization.usecase.ApproveAuthorizationRequestUseCase;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrantRequest;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingResult;
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
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
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

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractStudentDiscountIssuingIT extends AbstractIntegrationTest {

    protected static final StatusChangeActor ACTOR = StatusChangeActor.ofProcess("TEST_STUDENT_DISCOUNT_ISSUING");

    @Autowired
    protected BillingOperationPipeline pipeline;
    @Autowired
    protected GrantStudentDiscountOperation grantOperation;
    @Autowired
    protected RevokeStudentDiscountOperation revokeOperation;
    @Autowired
    protected StudentDiscountRepository discountRepository;
    @Autowired
    protected ApproveAuthorizationRequestUseCase approveRequest;
    @Autowired
    protected InvoiceIssuingService issuingService;
    @Autowired
    protected IssuingScenarioFactory factory;
    @Autowired
    protected com.red_escuela.backend.billing.note.usecase.CancelInvoiceBySystemUseCase systemCancellation;
    @Autowired
    protected com.red_escuela.backend.billing.note.service.CancelInvoiceOperation cancelOperation;
    @Autowired
    protected TransactionTemplate transactionTemplate;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected PersonRepository personRepository;
    @Autowired
    protected AddressRepository addressRepository;
    @Autowired
    protected RoleRepository roleRepository;

    protected Person director;
    protected Person administrative;
    protected IssuingScenarioFactory.Offering offering;
    protected IssuingScenarioFactory.Learner learner;
    protected BillingInvariantChecker invariants;
    protected Long templateId;
    protected Long cohortId;
    protected final List<Long> learnerStudentIds = new ArrayList<>();

    @BeforeEach
    void setUpIssuingScenario() {
        invariants = BillingInvariantChecker.startingNow(jdbc);
        today(LocalDate.of(2027, 3, 1));
        director = persistPerson("sdi.director", "DIRECTOR");
        administrative = persistPerson("sdi.administrative", "ADMINISTRATIVO");
        offering = factory.newProgram(Currency.ARS, 80000, 50000);
        learner = factory.newLearner(offering, null);
        learnerStudentIds.add(learner.studentId());
    }

    @AfterEach
    void tearDownIssuingScenario() {
        SecurityContextHolder.clearContext();
        jdbc.execute("alter table student_discount_status_change disable trigger trg_student_discount_status_change_append_only");
        try {
            learnerStudentIds.forEach(studentId -> {
                jdbc.update("delete from student_discount_status_change where student_discount_id in (select id from student_discount where student_id = ?)", studentId);
                jdbc.update("delete from student_discount where student_id = ?", studentId);
            });
        } finally {
            jdbc.execute("alter table student_discount_status_change enable trigger trg_student_discount_status_change_append_only");
        }
        String people = director.getId() + "," + administrative.getId();
        jdbc.update("delete from authorization_request_status_change where authorization_request_id in (select id from authorization_request where requested_by_id in (" + people + "))");
        jdbc.update("delete from authorization_request where requested_by_id in (" + people + ")");
    }

    protected Person persistPerson(String prefix, String roleName) {
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

    protected void authenticateAs(Person person) {
        String authority = "ROLE_" + person.getRoles().iterator().next().getName();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(person.getId().toString(), null, authority));
    }

    protected void today(LocalDate date) {
        MutableClock.INSTANCE.setDate(date);
    }

    protected void useCohortLearner(Percentage entryDiscount) {
        templateId = factory.newTemplate(offering, 2027);
        cohortId = factory.newCohort(templateId, CohortIntake.FIRST, LocalDate.of(2027, 3, 1), entryDiscount);
        learner = factory.newLearner(offering, cohortId);
        learnerStudentIds.add(learner.studentId());
    }

    protected IssuingScenarioFactory.Learner newCohortLearner() {
        IssuingScenarioFactory.Learner another = factory.newLearner(offering, cohortId);
        learnerStudentIds.add(another.studentId());
        return another;
    }

    protected IssuingScenarioFactory.Learner newPlainLearner() {
        IssuingScenarioFactory.Learner another = factory.newLearner(offering, null);
        learnerStudentIds.add(another.studentId());
        return another;
    }

    protected EnrollmentReference enrollment() {
        return EnrollmentReference.ofProgram(learner.programEnrollmentId());
    }

    protected static MonetaryAdjustment percent(String points) {
        return new MonetaryAdjustment.Proportional(Percentage.ofPoints(new BigDecimal(points)));
    }

    protected StudentDiscountGrantRequest scholarship(String points, YearMonth from, YearMonth to) {
        return new StudentDiscountGrantRequest(DiscountType.SCHOLARSHIP, null, percent(points), InvoiceType.MONTHLY_INSTALLMENT, from, to, "Situación económica");
    }

    protected StudentDiscountGrantRequest individual(InvoiceType invoiceType, MonetaryAdjustment calculation, YearMonth from, YearMonth to, String reason) {
        return new StudentDiscountGrantRequest(DiscountType.INDIVIDUAL, null, calculation, invoiceType, from, to, reason);
    }

    protected BillingOperationOutcome<StudentDiscount> grantAs(Person actor, EnrollmentReference target, StudentDiscountGrantRequest request) {
        authenticateAs(actor);
        boolean byDirector = actor.getId().equals(director.getId());
        return pipeline.runOperation(grantOperation, GrantStudentDiscountCommand.byPerson(target, request, actor.getId(), byDirector));
    }

    protected StudentDiscount grantAsDirector(StudentDiscountGrantRequest request) {
        return grantAsDirectorTo(enrollment(), request);
    }

    protected StudentDiscount grantAsDirectorTo(EnrollmentReference target, StudentDiscountGrantRequest request) {
        return ((BillingOperationOutcome.Processed<StudentDiscount>) grantAs(director, target, request)).result();
    }

    protected Long proposeGrant(StudentDiscountGrantRequest request) {
        return ((BillingOperationOutcome.AwaitingAuthorization<StudentDiscount>) grantAs(administrative, enrollment(), request)).authorizationRequestId();
    }

    protected void approveAsDirector(Long requestId) {
        authenticateAs(director);
        approveRequest.approve(requestId, "ok");
    }

    protected StudentDiscount revokeAsDirector(Long studentDiscountId, String reason) {
        authenticateAs(director);
        return ((BillingOperationOutcome.Processed<StudentDiscount>) pipeline.runOperation(revokeOperation,
                new RevokeStudentDiscountCommand(studentDiscountId, reason, director.getId(), true, LocalDate.now(MutableClock.INSTANCE)))).result();
    }

    protected void cancelAsDirector(Long invoiceId, com.red_escuela.backend.billing.note.domain.CreditNoteReason reason) {
        authenticateAs(director);
        pipeline.runOperation(cancelOperation, com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand.byPerson(invoiceId, reason, "Matrícula anulada", director.getId(), true));
    }

    protected InvoiceIssuingRequest installmentOf(IssuingScenarioFactory.Learner target, YearMonth period) {
        return new InvoiceIssuingRequest(target.studentId(), target.programEnrollmentId(), null, offering.programId(), null, null, FeeType.MONTHLY_INSTALLMENT,
                period, period.atDay(11), period.atDay(1), InvoiceIssuingReason.MONTHLY_RUN, target.selectionId(), null, null, false, null, null, null, null, null, ACTOR);
    }

    protected InvoiceIssuingRequest enrollmentFeeOf(IssuingScenarioFactory.Learner target, int slot, YearMonth period, boolean entry) {
        return new InvoiceIssuingRequest(target.studentId(), target.programEnrollmentId(), null, offering.programId(), null, cohortId, FeeType.ENROLLMENT_FEE,
                period, period.atDay(11), period.atDay(1), InvoiceIssuingReason.ENROLLMENT_FEE_ON_DEMAND, target.selectionId(), templateId, slot, entry, null, null, null,
                null, null, ACTOR);
    }

    protected InvoiceIssuingRequest adHocChargeOf(FeeType feeType, YearMonth period, String amount, String key) {
        return new InvoiceIssuingRequest(learner.studentId(), learner.programEnrollmentId(), null, offering.programId(), null, null, feeType, period, period.atDay(20),
                period.atDay(1), InvoiceIssuingReason.AD_HOC_CHARGE, null, null, null, false, NonNegativeMoney.of(new BigDecimal(amount), offering.currency()),
                "Cargo " + key, null, null, key, ACTOR);
    }

    protected Long issueInstallment(YearMonth period) {
        today(period.atDay(1));
        return issue(installmentOf(learner, period));
    }

    protected Long issue(InvoiceIssuingRequest request) {
        InvoiceIssuingResult result = issuingService.issueInvoiceInNewTransaction(request);
        assertThat(result).isInstanceOf(InvoiceIssuingResult.InvoiceIssued.class);
        return ((InvoiceIssuingResult.InvoiceIssued) result).invoice().getId();
    }

    protected List<Map<String, Object>> discountsOn(IssuingScenarioFactory.Learner target, FeeType feeType, YearMonth period) {
        return jdbc.queryForList("""
                select l.period, l.fee_type, d.discount_type, d.student_discount_id, d.base_amount, d.discount_amount, d.installment_number, d.installments_total,
                       d.concept, d.consumption_released_at
                from invoice_discount d join invoice_line l on l.id = d.invoice_line_id
                where l.program_enrollment_id = ? and l.fee_type = ? and l.period = ? order by d.id
                """, target.programEnrollmentId(), feeType.name(), Date.valueOf(period.atDay(1)));
    }

    protected List<Map<String, Object>> discountsOn(FeeType feeType, YearMonth period) {
        return discountsOn(learner, feeType, period);
    }

    protected Map<String, Object> onlyDiscountOn(FeeType feeType, YearMonth period) {
        List<Map<String, Object>> discounts = discountsOn(feeType, period);
        assertThat(discounts).as("discounts of %s %s".formatted(feeType, period)).hasSize(1);
        return discounts.get(0);
    }

    protected long discountRowsOf(Long studentDiscountId) {
        return jdbc.queryForObject("select count(*) from invoice_discount where student_discount_id = ?", Long.class, studentDiscountId);
    }

    protected static BigDecimal amount(Object value) {
        return (BigDecimal) value;
    }
}
