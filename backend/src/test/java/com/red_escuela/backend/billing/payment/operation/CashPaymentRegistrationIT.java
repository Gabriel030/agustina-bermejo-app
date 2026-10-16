package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.AbstractCurrentAccountIT;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.dto.PriceBreakdownResponse;
import com.red_escuela.backend.billing.service.InvoiceQueryService;
import com.red_escuela.backend.billing.events.AdmissionInvoicePaidEvent;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.service.EnrollmentSuspensionLiftedEvent;
import com.red_escuela.backend.notification.SuspensionLiftedEvent;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.time.MutableClock;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@RecordApplicationEvents
class CashPaymentRegistrationIT extends AbstractCurrentAccountIT {

    static final LocalDate TODAY = LocalDate.of(2027, 4, 15);

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    RoleRepository roleRepository;
    @Autowired
    JwtService jwtService;
    @Autowired
    EntityManager entityManager;
    @Autowired
    CurrentAccountAllocationService allocationService;
    @Autowired
    MeterRegistry meterRegistry;
    @Autowired
    ApplicationEvents applicationEvents;

    String administrative;
    String director;

    @BeforeEach
    void fixTheClockAndIssueStaffTokens() {
        MutableClock.INSTANCE.setDate(TODAY);
        administrative = tokenWithRole("ADMINISTRATIVO");
        director = tokenWithRole("DIRECTOR");
    }

    String tokenWithRole(String role) {
        return tokenOf(newStaffPerson(), role);
    }

    String tokenOf(Person person, String role) {
        return inTransaction(() -> {
            Person managed = entityManager.find(Person.class, person.getId());
            managed.getRoles().add(roleRepository.findByName(role).orElseThrow());
            return jwtService.generateAccessToken(managed);
        });
    }

    record Debtor(Long studentId, Long accountId) {
    }

    Debtor debtor() {
        Long studentId = newStudentId();
        return new Debtor(studentId, newAccountId(studentId, Currency.ARS));
    }

    ObjectNode bodyOf(String currency, String amount, String paidOn, String receipt, String reason, String fingerprint) {
        ObjectNode body = mapper.createObjectNode();
        body.putObject("amount").put("amount", new BigDecimal(amount)).put("currency", currency);
        if (paidOn != null) {
            body.put("paidOn", paidOn);
        }
        if (receipt != null) {
            body.put("receiptNumber", receipt);
        }
        if (reason != null) {
            body.put("backdateReason", reason);
        }
        body.put("previewFingerprint", fingerprint);
        return body;
    }

    JsonNode previewOf(Long studentId, String currency, String amount, String token) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.putObject("amount").put("amount", new BigDecimal(amount)).put("currency", currency);
        MvcResult result = mockMvc.perform(post("/students/" + studentId + "/cash-payments/preview").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    String fingerprintOf(Long studentId, String amount) throws Exception {
        return previewOf(studentId, "ARS", amount, administrative).get("previewFingerprint").asText();
    }

    MvcResult register(Long studentId, String key, ObjectNode body, String token) throws Exception {
        return mockMvc.perform(post("/students/" + studentId + "/cash-payments").header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body.toString())).andReturn();
    }

    MvcResult registerArs(Long studentId, String key, String amount, String paidOn, String reason, String token) throws Exception {
        return register(studentId, key, bodyOf("ARS", amount, paidOn, null, reason, fingerprintOf(studentId, amount)), token);
    }

    String codeOf(MvcResult result) {
        try {
            return jsonOf(result).get("code").asText();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    BigDecimal decimalOf(JsonNode money) {
        return new BigDecimal(money.get("amount").asText());
    }

    JsonNode jsonOf(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    String statusOfInvoice(Long invoiceId) {
        return jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoiceId);
    }

    String reasonOf(Long paymentId) {
        return jdbc.queryForObject("select reason from payment_status_change where payment_id = ? and event = 'CASH_PAYMENT_REGISTERED'", String.class, paymentId);
    }

    long paymentsOf(Long accountId) {
        return jdbc.queryForObject("select count(*) from payment where student_current_account_id = ?", Long.class, accountId);
    }

    long entriesOf(Long accountId) {
        return jdbc.queryForObject("select count(*) from current_account_entry where student_current_account_id = ?", Long.class, accountId);
    }

    Long programEnrollmentOf(Long studentId, TeachingMode mode) {
        return inTransaction(() -> {
            Program program = new Program();
            program.setName("Carrera " + System.nanoTime());
            program.setTotalYears(3);
            program.setTotalCreditsRequired(0);
            program.setTeachingMode(mode);
            entityManager.persist(program);
            ProgramEnrollment enrollment = new ProgramEnrollment();
            enrollment.setStudent(entityManager.find(Student.class, studentId));
            enrollment.setProgram(program);
            enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            enrollment.setEnrollmentDate(LocalDateTime.now());
            entityManager.persist(enrollment);
            return enrollment.getId();
        });
    }

    Long invoiceOfOffering(Debtor debtor, TeachingMode mode, String amount, LocalDate dueDate) {
        Long enrollmentId = programEnrollmentOf(debtor.studentId(), mode);
        Long invoiceId = issueAdministrativeFeeWithLedgerDebit(debtor.accountId(), amount, dueDate);
        jdbc.update("update invoice_line set program_enrollment_id = ? where invoice_id = ?", enrollmentId, invoiceId);
        return invoiceId;
    }

    @Test
    @Tag("D-139")
    @Tag("D-134")
    @Tag("D-136")
    void surplusStaysInFavorAndOnlyTheOldestInvoiceIsPaidIgnoringAnyInvoiceIdsSentByTheClient() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = issueAdministrativeFeeWithLedgerDebit(debtor.accountId(), "80000.00", LocalDate.of(2027, 3, 10));
        Long april = issueAdministrativeFeeWithLedgerDebit(debtor.accountId(), "80000.00", LocalDate.of(2027, 4, 10));
        JsonNode preview = previewOf(debtor.studentId(), "ARS", "100000.00", administrative);
        assertThat(preview.get("coveredInvoices")).hasSize(1);
        assertThat(preview.get("coveredInvoices").get(0).get("invoiceId").asLong()).isEqualTo(march);
        assertThat(decimalOf(preview.get("unallocatedAfterwards"))).isEqualByComparingTo("20000.00");
        ObjectNode body = bodyOf("ARS", "100000.00", null, "R-1", null, preview.get("previewFingerprint").asText());
        body.putArray("invoiceIds").add(april);

        MvcResult result = register(debtor.studentId(), "key-surplus", body, administrative);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode response = jsonOf(result);
        assertThat(response.get("allocatedInvoices")).hasSize(1);
        assertThat(response.get("allocatedInvoices").get(0).get("invoiceId").asLong()).isEqualTo(march);
        assertThat(response.get("allocatedFromRemainingCredit")).isEmpty();
        assertThat(decimalOf(response.get("unallocatedCredit"))).isEqualByComparingTo("20000.00");
        assertThat(statusOfInvoice(march)).isEqualTo("PAID");
        assertThat(statusOfInvoice(april)).isNotEqualTo("PAID");
        assertThat(amount("select unallocated_credit_amount from student_current_account where id = ?", debtor.accountId())).isEqualByComparingTo("20000.00");
        assertThat(jdbc.queryForObject("select status from payment where id = ?", String.class, response.get("paymentId").asLong())).isEqualTo("APPROVED");
        invariants.assertAllBillingInvariantsHold();
    }

    @Autowired
    InvoiceQueryService invoiceQueryService;

    @Test
    @Tag("D-376")
    void theInvoiceBreakdownShowsTheCashPaymentThatFinancedItWithItsMethodAndAmountTaken() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = issueAdministrativeFeeWithLedgerDebit(debtor.accountId(), "80000.00", LocalDate.of(2027, 3, 10));
        JsonNode preview = previewOf(debtor.studentId(), "ARS", "80000.00", administrative);

        MvcResult result = register(debtor.studentId(), "key-source", bodyOf("ARS", "80000.00", null, "R-S", null, preview.get("previewFingerprint").asText()), administrative);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        long paymentId = jsonOf(result).get("paymentId").asLong();
        PriceBreakdownResponse breakdown = invoiceQueryService.priceBreakdown(march);
        assertThat(breakdown.paymentsApplied()).singleElement().satisfies(payment -> {
            assertThat(payment.paymentId()).isEqualTo(paymentId);
            assertThat(payment.method()).isEqualTo("CASH");
            assertThat(payment.amount()).isEqualTo(ars("80000.00").value());
            assertThat(payment.paidAt()).isNotNull();
        });
        assertThat(breakdown.creditNotesUsedAsFreeCredit()).isEmpty();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-319")
    @Tag("D-207")
    void aPreviewPersistsNothingAndAnOutdatedPreviewIsRejectedWithNothingWritten() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = issueAdministrativeFeeWithLedgerDebit(debtor.accountId(), "80000.00", LocalDate.of(2027, 3, 10));
        Long april = issueAdministrativeFeeWithLedgerDebit(debtor.accountId(), "80000.00", LocalDate.of(2027, 4, 10));
        long entriesBefore = entriesOf(debtor.accountId());

        JsonNode preview = previewOf(debtor.studentId(), "ARS", "80000.00", administrative);

        assertThat(preview.get("coveredInvoices").get(0).get("invoiceId").asLong()).isEqualTo(march);
        assertThat(paymentsOf(debtor.accountId())).isZero();
        assertThat(entriesOf(debtor.accountId())).isEqualTo(entriesBefore);
        assertThat(jdbc.queryForObject("select count(*) from idempotency_record", Long.class)).isNotNull();
        inTransaction(() -> {
            StudentCurrentAccount account = service.lockStudentCurrentAccount(debtor.accountId());
            service.recordPaymentReceivedCredit(account, BillingTestFixtures.seedApprovedPaymentRow(account.getId()), ars("80000.00"), TODAY, "Transferencia aprobada");
            allocationService.allocateAccountCreditToOldestInvoices(account, TODAY, ACTOR);
            return null;
        });
        long entriesAfterTransfer = entriesOf(debtor.accountId());
        long paymentsAfterTransfer = paymentsOf(debtor.accountId());

        MvcResult outdated = register(debtor.studentId(), "key-outdated", bodyOf("ARS", "80000.00", null, null, null, preview.get("previewFingerprint").asText()),
                administrative);

        assertThat(outdated.getResponse().getStatus()).isEqualTo(409);
        assertThat(jsonOf(outdated).get("code").asText()).isEqualTo("CASH_PAYMENT_PREVIEW_OUTDATED");
        assertThat(paymentsOf(debtor.accountId())).isEqualTo(paymentsAfterTransfer);
        assertThat(entriesOf(debtor.accountId())).isEqualTo(entriesAfterTransfer);
        assertThat(jdbc.queryForObject("select count(*) from idempotency_record where idempotency_key = ?", Long.class, "key-outdated")).isZero();
        assertThat(statusOfInvoice(march)).isEqualTo("PAID");

        JsonNode fresh = previewOf(debtor.studentId(), "ARS", "80000.00", administrative);
        assertThat(fresh.get("coveredInvoices").get(0).get("invoiceId").asLong()).isEqualTo(april);
        MvcResult confirmed = register(debtor.studentId(), "key-fresh", bodyOf("ARS", "80000.00", null, null, null, fresh.get("previewFingerprint").asText()),
                administrative);

        assertThat(confirmed.getResponse().getStatus()).isEqualTo(201);
        assertThat(statusOfInvoice(april)).isEqualTo("PAID");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-319")
    void aPreviewOfAnotherAmountIsRejectedEvenIfCoverageAndLeftoverAreTheSame() throws Exception {
        Debtor debtor = debtor();
        issueAdministrativeFeeWithLedgerDebit(debtor.accountId(), "80000.00", LocalDate.of(2027, 3, 10));
        String fingerprintFor100k = fingerprintOf(debtor.studentId(), "100000.00");
        inTransaction(() -> {
            service.recordPaymentReceivedCredit(service.lockStudentCurrentAccount(debtor.accountId()), BillingTestFixtures.seedApprovedPaymentRow(debtor.accountId()), ars("10000.00"), TODAY, "Saldo que entra");
            return null;
        });
        long paymentsBefore = paymentsOf(debtor.accountId());

        MvcResult result = register(debtor.studentId(), "key-amount", bodyOf("ARS", "90000.00", null, null, null, fingerprintFor100k), administrative);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(jsonOf(result).get("code").asText()).isEqualTo("CASH_PAYMENT_PREVIEW_OUTDATED");
        assertThat(paymentsOf(debtor.accountId())).isEqualTo(paymentsBefore);
    }

    @Test
    @Tag("D-266")
    @Tag("D-169")
    void theAdministrativeWindowReachesDayFiveAndNotDaySixAndTheFutureIsRejected() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor dayFive = debtor();
        Debtor daySix = debtor();
        Debtor future = debtor();

        MvcResult fiveDaysBack = registerArs(dayFive.studentId(), "key-day5", "1000.00", "2027-04-10", null, administrative);
        MvcResult sixDaysBack = registerArs(daySix.studentId(), "key-day6", "1000.00", "2027-04-09", null, administrative);
        MvcResult tomorrow = registerArs(future.studentId(), "key-future", "1000.00", "2027-04-16", null, director);

        assertThat(fiveDaysBack.getResponse().getStatus()).isEqualTo(201);
        assertThat(sixDaysBack.getResponse().getStatus()).isEqualTo(400);
        assertThat(jsonOf(sixDaysBack).get("code").asText()).isEqualTo("CASH_PAYMENT_DATE_TOO_OLD");
        assertThat(paymentsOf(daySix.accountId())).isZero();
        assertThat(tomorrow.getResponse().getStatus()).isEqualTo(400);
        assertThat(jsonOf(tomorrow).get("code").asText()).isEqualTo("PAYMENT_DATE_IN_FUTURE");
        assertThat(paymentsOf(future.accountId())).isZero();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-266")
    @Tag("D-270")
    void theDirectorNeedsAReasonBeyondFiveDaysAndTheReasonIsBounded() throws Exception {
        Debtor withoutReason = debtor();
        Debtor withReason = debtor();
        Debtor tooLong = debtor();

        MvcResult missing = registerArs(withoutReason.studentId(), "key-no-reason", "1000.00", "2027-04-09", null, director);
        MvcResult provided = registerArs(withReason.studentId(), "key-reason", "1000.00", "2027-04-09", "Recibo cargado tarde", director);
        MvcResult invalid = registerArs(tooLong.studentId(), "key-long-reason", "1000.00", "2027-04-09", "m".repeat(501), director);

        assertThat(missing.getResponse().getStatus()).isEqualTo(400);
        assertThat(jsonOf(missing).get("code").asText()).isEqualTo("CASH_PAYMENT_BACKDATE_REASON_REQUIRED");
        assertThat(provided.getResponse().getStatus()).isEqualTo(201);
        assertThat(reasonOf(jsonOf(provided).get("paymentId").asLong()))
                .isEqualTo("Recibo cargado tarde");
        assertThat(invalid.getResponse().getStatus()).isEqualTo(400);
        assertThat(jsonOf(invalid).get("code").asText()).isEqualTo("CASH_PAYMENT_BACKDATE_REASON_INVALID");
        assertThat(paymentsOf(withoutReason.accountId()) + paymentsOf(tooLong.accountId())).isZero();
    }

    @Test
    @Tag("D-266")
    @Tag("D-136")
    void replayingTheSameKeyTheNextDayReturnsTheOriginalResultInsteadOfRevalidatingTheDate() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        ObjectNode body = bodyOf("ARS", "1000.00", "2027-04-10", null, null, fingerprintOf(debtor.studentId(), "1000.00"));
        MvcResult first = register(debtor.studentId(), "key-replay-next-day", body, administrative);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        MutableClock.INSTANCE.setDate(TODAY.plusDays(1));
        double replaysBefore = meterRegistry.counter("billing.idempotency.replays").count();

        MvcResult replay = register(debtor.studentId(), "key-replay-next-day", body, administrative);

        assertThat(replay.getResponse().getStatus()).isEqualTo(201);
        assertThat(jsonOf(replay)).isEqualTo(jsonOf(first));
        assertThat(paymentsOf(debtor.accountId())).isEqualTo(1);
        assertThat(meterRegistry.counter("billing.idempotency.replays").count()).isEqualTo(replaysBefore + 1);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-136")
    void theSameKeyWithAnotherAmountIsAConflictAndWritesNothingNew() throws Exception {
        Debtor debtor = debtor();
        MvcResult first = registerArs(debtor.studentId(), "key-conflict", "1000.00", null, null, administrative);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);

        MvcResult other = register(debtor.studentId(), "key-conflict", bodyOf("ARS", "2000.00", null, null, null, fingerprintOf(debtor.studentId(), "2000.00")),
                administrative);

        assertThat(other.getResponse().getStatus()).isEqualTo(409);
        assertThat(jsonOf(other).get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
        assertThat(paymentsOf(debtor.accountId())).isEqualTo(1);
    }

    @Test
    @Tag("D-308")
    void aCashPaymentBackdatedByThreeDaysAllocatesWithTheEffectiveDateOfTheDayItWasPaid() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = issueAdministrativeFeeWithLedgerDebit(debtor.accountId(), "80000.00", LocalDate.of(2027, 3, 10));

        MvcResult result = registerArs(debtor.studentId(), "key-backdated", "80000.00", "2027-04-12", null, administrative);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(jdbc.queryForObject("select effective_date from payment_allocation where invoice_id = ?", LocalDate.class, march)).isEqualTo(LocalDate.of(2027, 4, 12));
        assertThat(jdbc.queryForObject("select paid_on from payment where id = ?", LocalDate.class, jsonOf(result).get("paymentId").asLong()))
                .isEqualTo(LocalDate.of(2027, 4, 12));
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-254")
    @Tag("D-317")
    void cashDoesNotPayAnOnlineInvoiceWhenAHybridOneIsOpenEvenIfTheOnlineOneIsOlder() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long online = invoiceOfOffering(debtor, TeachingMode.ONLINE, "80000.00", LocalDate.of(2027, 3, 10));
        Long hybrid = invoiceOfOffering(debtor, TeachingMode.HYBRID, "80000.00", LocalDate.of(2027, 4, 10));

        MvcResult result = registerArs(debtor.studentId(), "key-online-skip", "80000.00", null, null, administrative);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(statusOfInvoice(hybrid)).isEqualTo("PAID");
        assertThat(statusOfInvoice(online)).isNotEqualTo("PAID");
        JsonNode response = jsonOf(result);
        assertThat(response.get("allocatedInvoices")).hasSize(1);
        assertThat(response.get("allocatedInvoices").get(0).get("invoiceId").asLong()).isEqualTo(hybrid);
        assertThat(response.get("allocatedFromRemainingCredit")).isEmpty();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-254")
    @Tag("D-317")
    void cashThatDoesNotCoverTheOnlineInvoiceLeavesItUnpaidAndTheMoneyInFavor() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long online = invoiceOfOffering(debtor, TeachingMode.ONLINE, "80000.00", LocalDate.of(2027, 3, 10));

        MvcResult result = registerArs(debtor.studentId(), "key-online-short", "50000.00", null, null, administrative);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(statusOfInvoice(online)).isNotEqualTo("PAID");
        assertThat(jsonOf(result).get("allocatedInvoices")).isEmpty();
        assertThat(jsonOf(result).get("allocatedFromRemainingCredit")).isEmpty();
        assertThat(amount("select unallocated_credit_amount from student_current_account where id = ?", debtor.accountId())).isEqualByComparingTo("50000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-316")
    @Tag("D-374")
    @Tag("D-134")
    void theSecondPassPaysTheOnlineInvoiceWithTheRemainingCreditInTheSameOperation() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long online = invoiceOfOffering(debtor, TeachingMode.ONLINE, "80000.00", LocalDate.of(2027, 3, 10));
        Long hybrid = invoiceOfOffering(debtor, TeachingMode.HYBRID, "80000.00", LocalDate.of(2027, 4, 10));
        JsonNode preview = previewOf(debtor.studentId(), "ARS", "160000.00", administrative);
        assertThat(preview.get("coveredInvoices")).hasSize(1);
        assertThat(preview.get("coveredInvoices").get(0).get("invoiceId").asLong()).isEqualTo(hybrid);
        assertThat(preview.get("coveredByRemainingCredit")).hasSize(1);
        assertThat(preview.get("coveredByRemainingCredit").get(0).get("invoiceId").asLong()).isEqualTo(online);

        MvcResult result = register(debtor.studentId(), "key-second-pass", bodyOf("ARS", "160000.00", null, null, null, preview.get("previewFingerprint").asText()),
                administrative);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode response = jsonOf(result);
        assertThat(response.get("allocatedInvoices").get(0).get("invoiceId").asLong()).isEqualTo(hybrid);
        assertThat(response.get("allocatedFromRemainingCredit")).hasSize(1);
        assertThat(response.get("allocatedFromRemainingCredit").get(0).get("invoiceId").asLong()).isEqualTo(online);
        assertThat(decimalOf(response.get("unallocatedCredit"))).isEqualByComparingTo("0.00");
        assertThat(statusOfInvoice(online)).isEqualTo("PAID");
        assertThat(statusOfInvoice(hybrid)).isEqualTo("PAID");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-316")
    @Tag("D-374")
    void theCashOfAnOnlyOnlineDebtorEndsPayingItInTheSameResponseWhenItCoversTheInvoice() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long online = invoiceOfOffering(debtor, TeachingMode.ONLINE, "80000.00", LocalDate.of(2027, 3, 10));

        MvcResult result = registerArs(debtor.studentId(), "key-only-online", "80000.00", null, null, administrative);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode response = jsonOf(result);
        assertThat(response.get("allocatedInvoices")).isEmpty();
        assertThat(response.get("allocatedFromRemainingCredit").get(0).get("invoiceId").asLong()).isEqualTo(online);
        assertThat(statusOfInvoice(online)).isEqualTo("PAID");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-136")
    void anAccountOfAnotherCurrencyIsNotTouchedAndARequestWithoutCurrencyIsRejected() throws Exception {
        Debtor debtor = debtor();
        Long arsInvoice = issueAdministrativeFeeWithLedgerDebit(debtor.accountId(), "80000.00", LocalDate.of(2027, 3, 10));
        String usdFingerprint = previewOf(debtor.studentId(), "USD", "100.00", administrative).get("previewFingerprint").asText();

        MvcResult usd = register(debtor.studentId(), "key-usd", bodyOf("USD", "100.00", null, null, null, usdFingerprint), administrative);

        assertThat(usd.getResponse().getStatus()).isEqualTo(201);
        assertThat(statusOfInvoice(arsInvoice)).isNotEqualTo("PAID");
        assertThat(jdbc.queryForObject("select count(*) from student_current_account where student_id = ?", Long.class, debtor.studentId())).isEqualTo(2L);
        ObjectNode withoutCurrency = mapper.createObjectNode();
        withoutCurrency.putObject("amount").put("amount", new BigDecimal("100.00"));
        withoutCurrency.put("previewFingerprint", usdFingerprint);
        MvcResult rejected = mockMvc.perform(post("/students/" + debtor.studentId() + "/cash-payments").header("Authorization", "Bearer " + administrative)
                .header("Idempotency-Key", "key-no-currency").contentType(MediaType.APPLICATION_JSON).content(withoutCurrency.toString())).andReturn();
        assertThat(rejected.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @Tag("D-136")
    void everyColumnAcceptsItsMaximumValueWithoutAServerError() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        String key = "k".repeat(100);
        String fingerprint = fingerprintOf(debtor.studentId(), "9999999999.99");

        MvcResult result = register(debtor.studentId(), key, bodyOf("ARS", "9999999999.99", "2027-04-01", "r".repeat(100), "m".repeat(500), fingerprint), director);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(jdbc.queryForObject("select idempotency_key from payment where student_current_account_id = ?", String.class, debtor.accountId())).hasSize(100);
        assertThat(reasonOf(jsonOf(result).get("paymentId").asLong())).hasSize(500);
        assertThat(jdbc.queryForObject("select method_details->>'receiptNumber' from payment where student_current_account_id = ?", String.class,
                debtor.accountId())).hasSize(100);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-136")
    void valuesBeyondTheColumnsAreRejectedWithABadRequest() throws Exception {
        Debtor debtor = debtor();
        String fingerprint = fingerprintOf(debtor.studentId(), "100.00");

        MvcResult longKey = register(debtor.studentId(), "k".repeat(101), bodyOf("ARS", "100.00", null, null, null, fingerprint), administrative);
        MvcResult longReceipt = register(debtor.studentId(), "key-long-receipt", bodyOf("ARS", "100.00", null, "r".repeat(101), null, fingerprint), administrative);
        MvcResult hugeAmount = register(debtor.studentId(), "key-huge", bodyOf("ARS", "10000000000.00", null, null, null, fingerprint), administrative);

        assertThat(longKey.getResponse().getStatus()).isEqualTo(400);
        assertThat(jsonOf(longKey).get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_INVALID");
        assertThat(longReceipt.getResponse().getStatus()).isEqualTo(400);
        assertThat(jsonOf(longReceipt).get("code").asText()).isEqualTo("CASH_PAYMENT_RECEIPT_NUMBER_INVALID");
        assertThat(hugeAmount.getResponse().getStatus()).isEqualTo(400);
        assertThat(jsonOf(hugeAmount).get("code").asText()).isEqualTo("CASH_PAYMENT_AMOUNT_INVALID");
        assertThat(paymentsOf(debtor.accountId())).isZero();
    }

    @Test
    @Tag("D-136")
    void aMissingStudentAnswers404WithItsCode() throws Exception {
        ObjectNode body = bodyOf("ARS", "100.00", null, null, null, "fingerprint");

        MvcResult result = register(999_999_999L, "key-missing-student", body, administrative);

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(jsonOf(result).get("code").asText()).isEqualTo("STUDENT_NOT_FOUND");
    }

    @Test
    @Tag("D-136")
    void theEndpointsAcceptOnlyTheirRolesAndAStudentSeesOnlyItsOwnPayments() throws Exception {
        Debtor own = debtor();
        Debtor other = debtor();
        registerArs(own.studentId(), "key-own", "1000.00", null, null, administrative);
        registerArs(other.studentId(), "key-other", "2000.00", null, null, administrative);
        Person ownPerson = inTransaction(() -> entityManager.find(Student.class, own.studentId()).getPerson());
        String student = tokenOf(ownPerson, "ALUMNO");
        String docente = tokenWithRole("DOCENTE");
        ObjectNode body = bodyOf("ARS", "1000.00", null, null, null, "x");
        String previewBody = "{\"amount\":{\"amount\":1.00,\"currency\":\"ARS\"}}";

        for (String token : List.of(student, docente)) {
            assertThat(mockMvc.perform(post("/students/" + own.studentId() + "/cash-payments").header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", "key-denied").contentType(MediaType.APPLICATION_JSON).content(body.toString())).andReturn().getResponse().getStatus())
                    .isEqualTo(403);
            assertThat(mockMvc.perform(post("/students/" + own.studentId() + "/cash-payments/preview").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(previewBody)).andReturn().getResponse().getStatus()).isEqualTo(403);
            assertThat(mockMvc.perform(get("/students/" + own.studentId() + "/payments").header("Authorization", "Bearer " + token)).andReturn().getResponse()
                    .getStatus()).isEqualTo(403);
        }
        assertThat(mockMvc.perform(get("/me/payments").header("Authorization", "Bearer " + administrative)).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mockMvc.perform(get("/students/" + own.studentId() + "/payments")).andReturn().getResponse().getStatus()).isIn(401, 403);
        assertThat(mockMvc.perform(get("/students/" + own.studentId() + "/payments").header("Authorization", "Bearer " + director)).andReturn().getResponse()
                .getStatus()).isEqualTo(200);
        MvcResult mine = mockMvc.perform(get("/me/payments").header("Authorization", "Bearer " + student)).andReturn();
        assertThat(mine.getResponse().getStatus()).isEqualTo(200);
        JsonNode payments = jsonOf(mine);
        assertThat(payments).hasSize(1);
        assertThat(decimalOf(payments.get(0).get("amount"))).isEqualByComparingTo("1000.00");
    }

    List<MvcResult> runTogether(List<java.util.concurrent.Callable<MvcResult>> requests) throws Exception {
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(requests.size());
        try {
            List<java.util.concurrent.Future<MvcResult>> futures = new java.util.ArrayList<>();
            for (java.util.concurrent.Callable<MvcResult> request : requests) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return request.call();
                }));
            }
            start.countDown();
            List<MvcResult> results = new java.util.ArrayList<>();
            for (java.util.concurrent.Future<MvcResult> future : futures) {
                results.add(future.get(60, java.util.concurrent.TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Tag("D-136")
    void twoThreadsWithTheSameKeyRegisterOnePaymentAndReplayTheOther() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = issueAdministrativeFeeWithLedgerDebit(debtor.accountId(), "80000.00", LocalDate.of(2027, 3, 10));
        ObjectNode body = bodyOf("ARS", "80000.00", null, null, null, fingerprintOf(debtor.studentId(), "80000.00"));
        double replaysBefore = meterRegistry.counter("billing.idempotency.replays").count();

        List<MvcResult> results = runTogether(List.of(() -> register(debtor.studentId(), "key-two-threads", body, administrative),
                () -> register(debtor.studentId(), "key-two-threads", body, administrative)));

        assertThat(results).extracting(result -> result.getResponse().getStatus()).containsOnly(201);
        assertThat(jsonOf(results.get(0))).isEqualTo(jsonOf(results.get(1)));
        assertThat(paymentsOf(debtor.accountId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from payment_allocation where invoice_id = ?", Long.class, march)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from current_account_entry where student_current_account_id = ? and entry_type = 'PAYMENT_RECEIVED'",
                Long.class, debtor.accountId())).isEqualTo(1L);
        assertThat(meterRegistry.counter("billing.idempotency.replays").count()).isEqualTo(replaysBefore + 1);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-319")
    @Tag("D-136")
    void twoThreadsWithDifferentKeysAndTheSamePreviewProduceOneCreatedAndOneOutdated() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        issueAdministrativeFeeWithLedgerDebit(debtor.accountId(), "80000.00", LocalDate.of(2027, 3, 10));
        ObjectNode body = bodyOf("ARS", "80000.00", null, null, null, fingerprintOf(debtor.studentId(), "80000.00"));

        List<MvcResult> results = runTogether(List.of(() -> register(debtor.studentId(), "key-first-thread", body, administrative),
                () -> register(debtor.studentId(), "key-second-thread", body, administrative)));

        assertThat(results).extracting(result -> result.getResponse().getStatus()).containsExactlyInAnyOrder(201, 409);
        MvcResult rejected = results.stream().filter(result -> result.getResponse().getStatus() == 409).findFirst().orElseThrow();
        assertThat(jsonOf(rejected).get("code").asText()).isEqualTo("CASH_PAYMENT_PREVIEW_OUTDATED");
        assertThat(paymentsOf(debtor.accountId())).isEqualTo(1);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-136")
    @Tag("D-144")
    void twoCashPaymentsAndAnInvoiceIssuanceAtTheSameTimeNeverLeaveAnUnexplainedConflict() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        issueAdministrativeFeeWithLedgerDebit(debtor.accountId(), "80000.00", LocalDate.of(2027, 3, 10));
        ObjectNode body = bodyOf("ARS", "80000.00", null, null, null, fingerprintOf(debtor.studentId(), "80000.00"));

        List<MvcResult> results = runTogether(List.of(() -> register(debtor.studentId(), "key-cash-one", body, administrative),
                () -> register(debtor.studentId(), "key-cash-two", body, administrative), () -> {
                    issueAdministrativeFeeWithLedgerDebit(debtor.accountId(), "50000.00", LocalDate.of(2027, 4, 10));
                    return null;
                }));

        List<MvcResult> cashResults = results.subList(0, 2);
        assertThat(cashResults).extracting(result -> result.getResponse().getStatus()).allMatch(status -> status == 201 || status == 409);
        cashResults.stream().filter(result -> result.getResponse().getStatus() == 409)
                .forEach(result -> assertThat(codeOf(result)).isEqualTo("CASH_PAYMENT_PREVIEW_OUTDATED"));
        assertThat(cashResults.stream().filter(result -> result.getResponse().getStatus() == 201).count()).isEqualTo(paymentsOf(debtor.accountId())).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from invoice where student_current_account_id = ?", Long.class, debtor.accountId())).isEqualTo(2L);
        invariants.assertAllBillingInvariantsHold();
    }

    @Autowired
    com.red_escuela.backend.billing.invoice.service.InvoiceLockingService invoiceLockingService;

    @Test
    @Tag("D-136")
    @Tag("D-144")
    void aCashPaymentWaitsForTheInvoiceLockOfTheOverdueTransitionInsteadOfFailingWithAnOptimisticConflict() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = issueAdministrativeFeeWithLedgerDebit(debtor.accountId(), "80000.00", LocalDate.of(2027, 3, 10));
        ObjectNode body = bodyOf("ARS", "80000.00", null, null, null, fingerprintOf(debtor.studentId(), "80000.00"));
        java.util.concurrent.CountDownLatch invoiceLocked = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch releaseTransition = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<?> transition = executor.submit(() -> inTransaction(() -> {
                com.red_escuela.backend.billing.invoice.domain.Invoice invoice = invoiceLockingService.lockInvoiceForUpdate(march);
                invoice.markInvoiceAsOverdue(TODAY, ACTOR);
                invoiceLocked.countDown();
                try {
                    releaseTransition.await(60, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
            assertThat(invoiceLocked.await(60, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            java.util.concurrent.Future<MvcResult> cash = executor.submit(() -> register(debtor.studentId(), "key-invoice-lock", body, administrative));

            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                    "select count(*) from pg_stat_activity where wait_event_type = 'Lock' and datname = current_database()", Long.class)).isPositive());
            assertThat(cash.isDone()).isFalse();
            releaseTransition.countDown();
            transition.get(60, java.util.concurrent.TimeUnit.SECONDS);

            MvcResult result = cash.get(60, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(result.getResponse().getStatus()).isEqualTo(201);
            assertThat(statusOfInvoice(march)).isEqualTo("PAID");
        } finally {
            releaseTransition.countDown();
            executor.shutdownNow();
        }
        invariants.assertAllBillingInvariantsHold();
    }

    Long invoiceOfEnrollment(Debtor debtor, Long enrollmentId, String amount, LocalDate dueDate) {
        Long invoiceId = issueAdministrativeFeeWithLedgerDebit(debtor.accountId(), amount, dueDate);
        jdbc.update("update invoice_line set program_enrollment_id = ? where invoice_id = ?", enrollmentId, invoiceId);
        return invoiceId;
    }

    void markOverdue(Long invoiceId) {
        jdbc.update("update invoice set payment_status = 'OVERDUE' where id = ?", invoiceId);
    }

    long admissionEventsOf(Long invoiceId) {
        return applicationEvents.stream(AdmissionInvoicePaidEvent.class).filter(event -> event.invoiceId().equals(invoiceId)).count();
    }

    @Test
    @Tag("D-231")
    @Tag("D-136")
    void payingAnAdmissionInvoiceWithCashPublishesTheAdmissionEventOnceAndAReplayDoesNotRepeatIt() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long admission = invoiceOfOffering(debtor, TeachingMode.IN_PERSON, "80000.00", LocalDate.of(2027, 4, 20));
        jdbc.update("update invoice set issuing_reason = 'PROGRAM_ADMISSION' where id = ?", admission);
        Long ordinary = invoiceOfOffering(debtor, TeachingMode.IN_PERSON, "50000.00", LocalDate.of(2027, 4, 25));
        String fingerprint = fingerprintOf(debtor.studentId(), "80000.00");

        MvcResult first = register(debtor.studentId(), "key-admission", bodyOf("ARS", "80000.00", null, null, null, fingerprint), administrative);
        MvcResult replay = register(debtor.studentId(), "key-admission", bodyOf("ARS", "80000.00", null, null, null, fingerprint), administrative);

        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(replay.getResponse().getStatus()).isEqualTo(201);
        assertThat(statusOfInvoice(admission)).isEqualTo("PAID");
        assertThat(admissionEventsOf(admission)).isEqualTo(1);
        assertThat(admissionEventsOf(ordinary)).isZero();
        assertThat(paymentsOf(debtor.accountId())).isEqualTo(1);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-231")
    void aPartialCashPaymentOfAnAdmissionInvoiceDoesNotPublishTheAdmissionEvent() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long admission = invoiceOfOffering(debtor, TeachingMode.IN_PERSON, "80000.00", LocalDate.of(2027, 4, 20));
        jdbc.update("update invoice set issuing_reason = 'PROGRAM_ADMISSION' where id = ?", admission);

        MvcResult result = registerArs(debtor.studentId(), "key-admission-partial", "30000.00", null, null, administrative);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(statusOfInvoice(admission)).isNotEqualTo("PAID");
        assertThat(admissionEventsOf(admission)).isZero();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-231")
    @Tag("D-134")
    void payingTheLastOverdueInvoiceLiftsTheSuspensionAndPayingTheFirstOneDoesNot() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long enrollmentId = programEnrollmentOf(debtor.studentId(), TeachingMode.IN_PERSON);
        Long march = invoiceOfEnrollment(debtor, enrollmentId, "80000.00", LocalDate.of(2027, 3, 10));
        Long april = invoiceOfEnrollment(debtor, enrollmentId, "80000.00", LocalDate.of(2027, 4, 10));
        markOverdue(march);
        markOverdue(april);
        jdbc.update("update program_enrollment set payment_status = 'SUSPENDED', payment_status_updated_at = ? where id = ?",
                LocalDateTime.of(2027, 4, 1, 8, 0), enrollmentId);

        MvcResult first = registerArs(debtor.studentId(), "key-lift-first", "80000.00", null, null, administrative);

        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(statusOfInvoice(march)).isEqualTo("PAID");
        assertThat(statusOfInvoice(april)).isEqualTo("OVERDUE");
        assertThat(jdbc.queryForObject("select payment_status from program_enrollment where id = ?", String.class, enrollmentId)).isEqualTo("SUSPENDED");
        assertThat(applicationEvents.stream(SuspensionLiftedEvent.class).count()).isZero();

        MvcResult second = registerArs(debtor.studentId(), "key-lift-second", "80000.00", null, null, administrative);

        assertThat(second.getResponse().getStatus()).isEqualTo(201);
        assertThat(statusOfInvoice(april)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select payment_status from program_enrollment where id = ?", String.class, enrollmentId)).isEqualTo("UP_TO_DATE");
        assertThat(applicationEvents.stream(SuspensionLiftedEvent.class).count()).isEqualTo(1);
        assertThat(applicationEvents.stream(EnrollmentSuspensionLiftedEvent.class)
                .filter(event -> event.enrollmentId().equals(enrollmentId)).count()).isEqualTo(1);
        invariants.assertAllBillingInvariantsHold();
    }
}
