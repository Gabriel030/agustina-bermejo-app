package com.red_escuela.backend.billing.annualupfront.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.configuration.domain.AnnualUpfrontSettings;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.discount.DiscountDraftBuilder;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.operation.AbstractStudentDiscountIssuingIT;
import com.red_escuela.backend.billing.discount.repository.DiscountDefinitionRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Tag("D-131")
@Tag("D-236")
@Tag("D-136")
@Tag("D-441")
@Tag("D-445")
class AnnualUpfrontRequestControllerIT extends AbstractStudentDiscountIssuingIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private ConfigurationVersionRepository configurationVersions;
    @Autowired
    private DiscountDefinitionRepository definitionRepository;

    private final List<Long> createdDefinitionIds = new ArrayList<>();

    @BeforeEach
    void setUpAnnualUpfrontRequest() {
        useCohortLearner(Percentage.ofPoints(BigDecimal.TEN));
        today(LocalDate.of(2027, 2, 10));
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 2, 10, 0));
        Long termId = jdbc.queryForObject("select term_id from program_year_billing_template where id = ?", Long.class, templateId);
        transactionTemplate.executeWithoutResult(status -> configurationVersions.saveAndFlush(ConfigurationVersion.publish(
                ConfigurationTopic.ofAnnualUpfront(offering.programId(), termId), new AnnualUpfrontSettings(LocalDate.of(2027, 3, 31), 48), LocalDate.of(2000, 1, 1),
                "Fixture de prueba", director.getId(), LocalDate.now(), null, Instant.now())));
    }

    @AfterEach
    void removeAnnualUpfrontRequests() {
        jdbc.update("delete from annual_upfront_request");
        jdbc.update("delete from idempotency_record where operation_type = 'REQUEST_ANNUAL_UPFRONT'");
        jdbc.execute("alter table discount_definition_status_change disable trigger trg_discount_definition_status_change_append_only");
        jdbc.execute("alter table student_discount_status_change disable trigger trg_student_discount_status_change_append_only");
        try {
            createdDefinitionIds.forEach(id -> {
                jdbc.update("delete from invoice_discount where student_discount_id in (select id from student_discount where discount_definition_id = ?)", id);
                jdbc.update("delete from student_discount_status_change where student_discount_id in (select id from student_discount where discount_definition_id = ?)", id);
                jdbc.update("delete from student_discount where discount_definition_id = ?", id);
                jdbc.update("delete from discount_definition_status_change where discount_definition_id = ?", id);
                jdbc.update("delete from discount_definition where id = ?", id);
            });
        } finally {
            jdbc.execute("alter table discount_definition_status_change enable trigger trg_discount_definition_status_change_append_only");
            jdbc.execute("alter table student_discount_status_change enable trigger trg_student_discount_status_change_append_only");
        }
    }

    private String studentToken() {
        return transactionTemplate.execute(status -> {
            Person person = studentRepository.findById(learner.studentId()).orElseThrow().getPerson();
            person.getRoles().add(roleRepository.findByName("ALUMNO").orElseThrow());
            personRepository.save(person);
            return jwtService.generateAccessToken(person);
        });
    }

    private MvcResult request(boolean accepted, String version, int expectedStatus) throws Exception {
        return request(accepted, version, UUID.randomUUID().toString(), studentToken(), expectedStatus);
    }

    private MvcResult request(boolean accepted, String version, String idempotencyKey, String token, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder builder = post("/me/program-enrollments/" + learner.programEnrollmentId() + "/annual-upfront-requests").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"termsAccepted\":" + accepted + ",\"termsVersion\":\"" + version + "\"}");
        return mockMvc.perform(idempotencyKey == null ? builder : builder.header("Idempotency-Key", idempotencyKey)).andExpect(status().is(expectedStatus)).andReturn();
    }

    private void publishAnnualPromotion() {
        DiscountDefinition saved = transactionTemplate.execute(status -> definitionRepository.saveAndFlush(DiscountDefinition.publishDiscountDefinition(
                DiscountDraftBuilder.annualUpfront().withPercent("10").availableFrom(LocalDate.of(2027, 1, 1)).build(), director.getId(), Instant.now())));
        createdDefinitionIds.add(saved.getId());
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

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
        String body = request(true, "v1", null, studentToken(), 400).getResponse().getContentAsString();

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
    void requestingWithoutAnyPromotionCreatesTheRequestAtFullPrice() throws Exception {
        JsonNode body = objectMapper.readTree(request(true, "v1", 201).getResponse().getContentAsString());

        assertThat(new BigDecimal(body.get("total").get("amount").asText())).isEqualByComparingTo("800000.00");
        assertThat(count("select count(*) from student_discount where origin = 'ANNUAL_UPFRONT_REQUEST'")).isZero();
    }
}
