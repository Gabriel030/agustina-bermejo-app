package com.red_escuela.backend.billing.annualupfront.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.red_escuela.backend.billing.discount.DiscountDraftBuilder;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.invoice.operation.IssuingScenarioFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@AutoConfigureMockMvc
@Tag("D-131")
@Tag("D-236")
@Tag("D-136")
@Tag("D-441")
@Tag("D-445")
class AnnualUpfrontRequestControllerIT extends AbstractAnnualUpfrontRequestIT {

    @Test
    void requestingTheYearLeavesAnAwaitingPaymentInvoiceWithTheWinningDiscountAndTheReservedPlace() throws Exception {
        publishAnnualPromotion();
        long movementsBefore = count("select count(*) from current_account_entry");

        JsonNode body = objectMapper.readTree(request(true, "v1", 201).getResponse().getContentAsString());

        assertThat(body.get("status").asText()).isEqualTo("AWAITING_PAYMENT");
        assertThat(body.get("paymentDeadline").asText()).isEqualTo("2027-03-04T10:00:00");
        assertThat(new BigDecimal(body.get("total").get("amount").asText())).isEqualByComparingTo("720000.00");
        Long invoiceId = body.get("invoiceId").asLong();
        assertThat(count("select count(*) from invoice_line where invoice_id = " + invoiceId + " and fee_type = 'MONTHLY_INSTALLMENT'")).isEqualTo(10);
        assertThat(count("select count(*) from invoice_line where invoice_id = " + invoiceId + " and fee_type = 'ENROLLMENT_FEE'")).isZero();
        assertThat(count("select count(*) from invoice where id = " + invoiceId + " and document_status = 'AWAITING_PAYMENT' and invoice_type = 'ANNUAL_UPFRONT'")).isEqualTo(1);
        assertThat(count("select count(*) from invoice_discount where invoice_line_id in (select id from invoice_line where invoice_id = " + invoiceId + ")")).isEqualTo(10);
        assertThat(count("select count(*) from student_discount where origin = 'ANNUAL_UPFRONT_REQUEST' and status = 'ACTIVE' and program_enrollment_id = "
                + learner.programEnrollmentId())).isEqualTo(1);
        assertThat(count("select count(*) from current_account_entry")).isEqualTo(movementsBefore);
        assertThat(count("select count(*) from annual_upfront_request where accepted_terms_version = 'v1' and length(accepted_terms_text) > 0 and invoice_id = " + invoiceId)).isEqualTo(1);
    }

    @Test
    void requestingWithoutAcceptingTheTermsIsRejectedAndCreatesNothing() throws Exception {
        long invoicesBefore = count("select count(*) from invoice where invoice_type = 'ANNUAL_UPFRONT'");

        request(false, "v1", 400);
        request(true, "v0", 400);

        assertThat(count("select count(*) from invoice where invoice_type = 'ANNUAL_UPFRONT'")).isEqualTo(invoicesBefore);
        assertThat(count("select count(*) from annual_upfront_request")).isZero();
    }

    @Test
    void aSecondRequestWhileTheFirstIsAwaitingPaymentGetsTheAlreadyRequestedConflict() throws Exception {
        request(true, "v1", 201);

        String body = request(true, "v1", 409).getResponse().getContentAsString();

        assertThat(body).contains("ANNUAL_UPFRONT_PAYMENT_ALREADY_REQUESTED");
        assertThat(count("select count(*) from annual_upfront_request")).isEqualTo(1);
    }

    @Test
    void repeatingTheSameIdempotencyKeyReplaysTheFirstResponseAndCreatesOneInvoice() throws Exception {
        String token = studentToken();
        long invoicesBefore = count("select count(*) from invoice where invoice_type = 'ANNUAL_UPFRONT'");

        String first = request(true, "v1", "annual-key-1", token, 201).getResponse().getContentAsString();
        String replay = request(true, "v1", "annual-key-1", token, 201).getResponse().getContentAsString();

        assertThat(objectMapper.readTree(replay)).isEqualTo(objectMapper.readTree(first));
        assertThat(count("select count(*) from annual_upfront_request")).isEqualTo(1);
        assertThat(count("select count(*) from invoice where invoice_type = 'ANNUAL_UPFRONT'")).isEqualTo(invoicesBefore + 1);
    }

    @Test
    void requestingWithoutAnIdempotencyKeyIsRejectedAndCreatesNothing() throws Exception {
        String token = studentToken();
        request(true, "v1", null, token, 400);
        String body = request(true, "v1", " ", token, 400).getResponse().getContentAsString();

        assertThat(body).contains("IDEMPOTENCY_KEY_INVALID");
        assertThat(count("select count(*) from annual_upfront_request")).isZero();
    }

    @Test
    void twoSimultaneousRequestsLeaveOneInvoiceAndTheLoserGetsTheAlreadyRequestedConflict() throws Exception {
        String token = studentToken();
        long invoicesBefore = count("select count(*) from invoice where invoice_type = 'ANNUAL_UPFRONT'");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> outcomes = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                String key = "annual-concurrent-" + index;
                outcomes.add(executor.submit(() -> {
                    start.await();
                    return mockMvc.perform(post("/me/program-enrollments/" + learner.programEnrollmentId() + "/annual-upfront-requests").header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{\"termsAccepted\":true,\"termsVersion\":\"v1\"}"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> outcome : outcomes) {
                statuses.add(outcome.get(60, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(count("select count(*) from invoice where invoice_type = 'ANNUAL_UPFRONT'")).isEqualTo(invoicesBefore + 1);
        assertThat(count("select count(*) from annual_upfront_request")).isEqualTo(1);
    }

    @Test
    @Tag("D-298")
    @Tag("D-240")
    @Tag("D-295")
    void twoStudentsRequestingAtOnceForAPromotionWithOnePlaceLeaveOneReservedDiscountAndTheOtherAtFullPrice() throws Exception {
        DiscountDefinition promotion = publishAnnualPromotion(DiscountDraftBuilder.annualUpfront().withPercent("10").availableFrom(LocalDate.of(2027, 1, 1))
                .forProgram(offering.programId()).withEnrollmentWindow(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31)).withQuota(1));
        IssuingScenarioFactory.Learner other = newCohortLearner();
        List<IssuingScenarioFactory.Learner> learners = List.of(learner, other);
        List<String> tokens = learners.stream().map(this::studentTokenOf).toList();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<BigDecimal> totals = new ArrayList<>();
        try {
            List<Future<String>> outcomes = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                IssuingScenarioFactory.Learner requester = learners.get(index);
                String token = tokens.get(index);
                String key = "annual-quota-" + index;
                outcomes.add(executor.submit(() -> {
                    start.await();
                    MvcResult result = mockMvc.perform(post("/me/program-enrollments/" + requester.programEnrollmentId() + "/annual-upfront-requests").header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{\"termsAccepted\":true,\"termsVersion\":\"v1\"}")).andReturn();
                    assertThat(result.getResponse().getStatus()).isEqualTo(201);
                    return result.getResponse().getContentAsString();
                }));
            }
            start.countDown();
            for (Future<String> outcome : outcomes) {
                totals.add(new BigDecimal(objectMapper.readTree(outcome.get(60, TimeUnit.SECONDS)).get("total").get("amount").asText()));
            }
        } finally {
            executor.shutdownNow();
        }
        assertThat(totals).usingElementComparator(BigDecimal::compareTo).containsExactlyInAnyOrder(new BigDecimal("720000.00"), new BigDecimal("800000.00"));
        assertThat(count("select count(*) from student_discount where origin = 'ANNUAL_UPFRONT_REQUEST' and status = 'ACTIVE' and discount_definition_id = " + promotion.getId())).isEqualTo(1);
        assertThat(count("select count(*) from annual_upfront_request where status = 'AWAITING_PAYMENT'")).isEqualTo(2);
    }

    @Test
    void requestingWithoutAnyPromotionCreatesTheRequestAtFullPrice() throws Exception {
        JsonNode body = objectMapper.readTree(request(true, "v1", 201).getResponse().getContentAsString());

        assertThat(new BigDecimal(body.get("total").get("amount").asText())).isEqualByComparingTo("800000.00");
        assertThat(count("select count(*) from student_discount where origin = 'ANNUAL_UPFRONT_REQUEST'")).isZero();
    }
}
