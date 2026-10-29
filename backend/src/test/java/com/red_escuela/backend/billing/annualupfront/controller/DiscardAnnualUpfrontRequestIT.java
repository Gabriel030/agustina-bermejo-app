package com.red_escuela.backend.billing.annualupfront.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.red_escuela.backend.billing.invoice.operation.IssuingScenarioFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Tag("D-443")
@Tag("D-198")
@Tag("D-286")
class DiscardAnnualUpfrontRequestIT extends AbstractAnnualUpfrontRequestIT {

    private JsonNode requestOf(IssuingScenarioFactory.Learner someLearner, String token) throws Exception {
        return objectMapper.readTree(requestOf(someLearner, true, "v1", UUID.randomUUID().toString(), token, 201).getResponse().getContentAsString());
    }

    private MvcResult discard(Long requestId, String token, int expectedStatus) throws Exception {
        return mockMvc.perform(post("/me/annual-upfront-requests/" + requestId + "/discard").header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus)).andReturn();
    }

    @Test
    void discardingARequestAwaitingPaymentDiscardsItsInvoiceFreesTheChargeKeysAndRevokesTheReservedDiscount() throws Exception {
        publishAnnualPromotion();
        JsonNode requested = objectMapper.readTree(request(true, "v1", 201).getResponse().getContentAsString());
        Long invoiceId = requested.get("invoiceId").asLong();
        Long requestId = requested.get("id").asLong();

        JsonNode body = objectMapper.readTree(discard(requestId, studentToken(), 200).getResponse().getContentAsString());

        assertThat(body.get("status").asText()).isEqualTo("DISCARDED");
        assertThat(body.get("discardReason").asText()).isEqualTo("CANCELLED_BY_STUDENT");
        assertThat(body.get("invoiceId").asLong()).isEqualTo(invoiceId);
        assertThat(count("select count(*) from annual_upfront_request where id = " + requestId + " and status = 'DISCARDED' and discard_reason = 'CANCELLED_BY_STUDENT' and decided_at is not null"))
                .isEqualTo(1);
        assertThat(count("select count(*) from invoice where id = " + invoiceId + " and document_status = 'DISCARDED'")).isEqualTo(1);
        assertThat(count("select count(*) from invoice_line where invoice_id = " + invoiceId + " and charge_key_released_at is null")).isZero();
        assertThat(count("select count(*) from invoice_discount where invoice_line_id in (select id from invoice_line where invoice_id = " + invoiceId + ") and consumption_released_at is null"))
                .isZero();
        assertThat(count("select count(*) from student_discount where origin = 'ANNUAL_UPFRONT_REQUEST' and status = 'REVOKED' and program_enrollment_id = " + learner.programEnrollmentId()))
                .isEqualTo(1);
        assertThat(count("select count(*) from student_discount where status = 'ACTIVE' and origin = 'ANNUAL_UPFRONT_REQUEST' and program_enrollment_id = " + learner.programEnrollmentId())).isZero();
    }

    @Test
    void discardingStillWorksWhenTheStaffAlreadyRevokedTheReservedDiscount() throws Exception {
        publishAnnualPromotion();
        JsonNode requested = objectMapper.readTree(request(true, "v1", 201).getResponse().getContentAsString());
        jdbc.update("update student_discount set status = 'REVOKED', revoked_at = now(), revoked_by_id = ?, revocation_reason = 'Revocación del personal', revoked_from_period = from_period "
                + "where origin = 'ANNUAL_UPFRONT_REQUEST' and program_enrollment_id = ?", director.getId(), learner.programEnrollmentId());

        discard(requested.get("id").asLong(), studentToken(), 200);

        assertThat(count("select count(*) from invoice where id = " + requested.get("invoiceId").asLong() + " and document_status = 'DISCARDED'")).isEqualTo(1);
        assertThat(count("select count(*) from invoice_discount where invoice_line_id in (select id from invoice_line where invoice_id = " + requested.get("invoiceId").asLong()
                + ") and consumption_released_at is null")).isZero();
    }

    @Test
    void discardingARequestWhoseReceiptIsUnderReviewIsAConflictAndChangesNothing() throws Exception {
        Long requestId = objectMapper.readTree(request(true, "v1", 201).getResponse().getContentAsString()).get("id").asLong();
        jdbc.update("update annual_upfront_request set status = 'PAYMENT_UNDER_REVIEW' where id = ?", requestId);

        discard(requestId, studentToken(), 409);

        assertThat(count("select count(*) from annual_upfront_request where id = " + requestId + " and status = 'PAYMENT_UNDER_REVIEW' and discard_reason is null")).isEqualTo(1);
        assertThat(count("select count(*) from invoice where id = (select invoice_id from annual_upfront_request where id = " + requestId + ") and document_status = 'AWAITING_PAYMENT'")).isEqualTo(1);
    }

    @Test
    void discardingARequestOfAnotherStudentIsNotFoundAndChangesNothing() throws Exception {
        Long requestId = objectMapper.readTree(request(true, "v1", 201).getResponse().getContentAsString()).get("id").asLong();
        String otherStudentToken = studentTokenOf(newCohortLearner());

        String foreignMessage = discard(requestId, otherStudentToken, 404).getResponse().getContentAsString();
        String unknownMessage = discard(987654322L, otherStudentToken, 404).getResponse().getContentAsString();

        assertThat(foreignMessage.replace(String.valueOf(requestId), "ID")).isEqualTo(unknownMessage.replace("987654322", "ID"));

        assertThat(count("select count(*) from annual_upfront_request where id = " + requestId + " and status = 'AWAITING_PAYMENT'")).isEqualTo(1);
    }

    @Test
    void discardingAnUnknownRequestIsNotFound() throws Exception {
        discard(987654321L, studentToken(), 404);
    }

    @Test
    void discardingTwiceGetsTheConflictOfTheSecondDiscardAndLeavesTheFirstOutcome() throws Exception {
        Long requestId = objectMapper.readTree(request(true, "v1", 201).getResponse().getContentAsString()).get("id").asLong();
        String token = studentToken();

        discard(requestId, token, 200);
        discard(requestId, token, 409);

        assertThat(count("select count(*) from annual_upfront_request where id = " + requestId + " and status = 'DISCARDED' and discard_reason = 'CANCELLED_BY_STUDENT'")).isEqualTo(1);
    }

    @Test
    void afterDiscardingTheStudentCanRequestTheYearAgain() throws Exception {
        String token = studentToken();
        Long firstId = requestOf(learner, token).get("id").asLong();
        discard(firstId, token, 200);

        JsonNode second = requestOf(learner, token);

        assertThat(second.get("id").asLong()).isNotEqualTo(firstId);
        assertThat(second.get("status").asText()).isEqualTo("AWAITING_PAYMENT");
        assertThat(count("select count(*) from annual_upfront_request where program_enrollment_id = " + learner.programEnrollmentId() + " and status = 'AWAITING_PAYMENT'")).isEqualTo(1);
    }
}
