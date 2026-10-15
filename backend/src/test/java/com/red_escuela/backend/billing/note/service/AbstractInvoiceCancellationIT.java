package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class AbstractInvoiceCancellationIT extends AbstractCreditNoteIT {

    protected static final LocalDate DUE_DATE = LocalDate.of(2027, 3, 10);

    protected record Scenario(Long studentId, Long accountId, Long invoiceId) {
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
}
