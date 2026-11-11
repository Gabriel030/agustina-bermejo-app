package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDraftHeader;
import com.red_escuela.backend.billing.invoice.domain.InvoiceFactory;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.StudentSnapshot;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.discount;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class AbstractInvoiceCancellationIT extends AbstractCreditNoteIT {

    protected static final LocalDate DUE_DATE = LocalDate.of(2027, 3, 10);

    protected record Scenario(Long studentId, Long accountId, Long invoiceId) {
    }

    protected record DiscountedInvoice(Long invoiceId, Long programEnrollmentId) {
    }

    @AfterEach
    void resetClock() {
        MutableClock.INSTANCE.reset();
    }

    protected Scenario unpaidInvoice(String amount) {
        Long studentId = newStudentId();
        Long accountId = newAccountId(studentId, Currency.ARS);
        return new Scenario(studentId, accountId, issueInvoiceOfStudent(studentId, accountId, amount, DUE_DATE));
    }

    protected void markOverdue(Long invoiceId) {
        BillingTestFixtures.markInvoiceAsOverdueOn(invoiceRepository.findById(invoiceId).orElseThrow(), DUE_DATE.plusDays(8));
    }

    protected String cancellationBody(String reason, String justification) {
        return "{\"reason\":\"" + reason + "\",\"justification\":" + (justification == null ? "null" : "\"" + justification + "\"") + "}";
    }

    protected MvcResult requestCancellation(Long invoiceId, Staff staff, String body, int expectedStatus) throws Exception {
        return mockMvc.perform(post("/invoices/" + invoiceId + "/cancellations").header("Authorization", "Bearer " + staff.token())
                .contentType(APPLICATION_JSON).content(body)).andExpect(status().is(expectedStatus)).andReturn();
    }

    protected MvcResult requestCreditNote(Long invoiceId, Staff staff, String body, int expectedStatus) throws Exception {
        return mockMvc.perform(post("/invoices/" + invoiceId + "/credit-notes").header("Authorization", "Bearer " + staff.token())
                .contentType(APPLICATION_JSON).content(body)).andExpect(status().is(expectedStatus)).andReturn();
    }

    protected long pendingRequestId(MvcResult result) throws Exception {
        return Long.parseLong(com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.authorizationRequestId").toString());
    }

    protected void decide(String action, long requestId, Staff staff, int expectedStatus) throws Exception {
        mockMvc.perform(post("/authorization-requests/" + requestId + "/" + action).header("Authorization", "Bearer " + staff.token())
                .contentType(APPLICATION_JSON).content("{\"decisionReason\":\"ok\"}")).andExpect(status().is(expectedStatus));
    }

    protected String paymentStatusOf(Long invoiceId) {
        return jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoiceId);
    }

    protected long creditNoteCountOf(Long invoiceId) {
        return jdbc.queryForObject("select count(*) from credit_note where invoice_id = ?", Long.class, invoiceId);
    }

    protected Long newPersonId() {
        return newStaffPerson().getId();
    }

    protected Staff studentStaff(Long studentId) {
        Long personId = studentPerson(studentId).getId();
        String token = inTransaction(() -> {
            Person managed = entityManager.find(Person.class, personId);
            managed.getRoles().add(roleRepository.findByName("ALUMNO").orElseThrow());
            return jwtService.generateAccessToken(managed);
        });
        return new Staff(personId, token);
    }

    protected Person studentPerson(Long studentId) {
        return studentRepository.findById(studentId).orElseThrow().getPerson();
    }

    private static InvoiceLineDraft annualLine(int number, long tag, Long priceRevisionId) {
        return new InvoiceLineDraft(com.red_escuela.backend.billing.invoice.domain.FeeType.MONTHLY_INSTALLMENT, "Cuota " + number,
                java.time.YearMonth.of(2027, 2 + number), null, null, null, null, priceRevisionId, "ANN:" + tag + ":" + number, ars("1000.00"));
    }

    protected DiscountedInvoice issueDiscountedAnnualInvoiceOfNewEnrollment(Long studentId, Long accountId) {
        return inTransaction(() -> {
            Person person = studentPerson(studentId);
            StudentSnapshot snapshot = new StudentSnapshot(person.getName() + " " + person.getLastname(), person.getDni(), "Calle 1");
            InvoiceDraftHeader header = new InvoiceDraftHeader(accountId, Currency.ARS, InvoiceDocumentType.INVOICE, InvoiceType.ANNUAL_UPFRONT,
                    InvoiceIssuingReason.ANNUAL_UPFRONT_PAYMENT, null);
            long tag = System.nanoTime();
            Long programId = jdbc.queryForObject("insert into program (name, status, total_years, currency) values (?, 'ACTIVE', 1, 'ARS') returning id", Long.class, "Fixture " + tag);
            Long revisionId = com.red_escuela.backend.billing.support.PriceFixtures.persistPriceVersion(programId, null, com.red_escuela.backend.billing.invoice.domain.FeeType.MONTHLY_INSTALLMENT, "Test", com.red_escuela.backend.shared.domain.Money.of(new java.math.BigDecimal("1000.00"), com.red_escuela.backend.shared.domain.Currency.ARS), java.time.LocalDate.of(2027, 1, 1), person.getId());
            Long enrollmentId = jdbc.queryForObject("insert into program_enrollment (student_id, program_id, status, enrollment_date) values (?, ?, 'ENROLLED', now()) returning id",
                    Long.class, studentId, programId);
            Long studentDiscountId = jdbc.queryForObject(
                    "insert into student_discount (program_enrollment_id, student_id, currency, discount_type, applicable_invoice_type, origin, calculation, priority, from_period, "
                            + "to_period, status, granted_at, granted_by_id, reason) values (?, ?, 'ARS', 'INDIVIDUAL', 'ENROLLMENT_FEE', 'DIRECT_GRANT', "
                            + "cast('{\"type\":\"PROPORTIONAL\",\"fraction\":\"0.5\"}' as jsonb), 1, date '2027-03-01', date '2027-03-01', 'ACTIVE', now(), ?, 'motivo') returning id",
                    Long.class, enrollmentId, studentId, person.getId());
            List<InvoiceLineDraft> lines = List.of(annualLine(1, tag, revisionId), annualLine(2, tag, revisionId), annualLine(3, tag, revisionId));
            Invoice draft = InvoiceFactory.createAnnualUpfrontInvoiceDraft(header, lines,
                    Map.of(1, discount("1000.00", "333.33", studentDiscountId), 2, discount("1000.00", "333.33", studentDiscountId), 3, discount("1000.00", "333.34", studentDiscountId)), ACTOR);
            draft.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), DUE_DATE.minusDays(9), DUE_DATE, snapshot, ACTOR);
            Invoice saved = invoiceRepository.save(draft);
            service.recordInvoiceIssuedDebit(service.lockStudentCurrentAccount(accountId), saved, DUE_DATE.minusDays(9));
            return new DiscountedInvoice(saved.getId(), enrollmentId);
        });
    }
}
