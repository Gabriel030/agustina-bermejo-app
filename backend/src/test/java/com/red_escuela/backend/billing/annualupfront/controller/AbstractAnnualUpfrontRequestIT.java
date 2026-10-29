package com.red_escuela.backend.billing.annualupfront.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.configuration.domain.AnnualUpfrontSettings;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.discount.DiscountDraftBuilder;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.operation.AbstractStudentDiscountIssuingIT;
import com.red_escuela.backend.billing.discount.repository.DiscountDefinitionRepository;
import com.red_escuela.backend.billing.invoice.operation.IssuingScenarioFactory;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class AbstractAnnualUpfrontRequestIT extends AbstractStudentDiscountIssuingIT {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected JwtService jwtService;
    @Autowired
    protected StudentRepository studentRepository;
    @Autowired
    protected ConfigurationVersionRepository configurationVersions;
    @Autowired
    protected DiscountDefinitionRepository definitionRepository;

    protected final List<Long> createdDefinitionIds = new ArrayList<>();

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

    protected String studentToken() {
        return studentTokenOf(learner);
    }

    protected String studentTokenOf(IssuingScenarioFactory.Learner someLearner) {
        return transactionTemplate.execute(status -> {
            Person person = studentRepository.findById(someLearner.studentId()).orElseThrow().getPerson();
            person.getRoles().add(roleRepository.findByName("ALUMNO").orElseThrow());
            personRepository.save(person);
            return jwtService.generateAccessToken(person);
        });
    }

    protected MvcResult request(boolean accepted, String version, int expectedStatus) throws Exception {
        return request(accepted, version, UUID.randomUUID().toString(), studentToken(), expectedStatus);
    }

    protected MvcResult request(boolean accepted, String version, String idempotencyKey, String token, int expectedStatus) throws Exception {
        return requestOf(learner, accepted, version, idempotencyKey, token, expectedStatus);
    }

    protected MvcResult requestOf(IssuingScenarioFactory.Learner someLearner, boolean accepted, String version, String idempotencyKey, String token, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder builder = post("/me/program-enrollments/" + someLearner.programEnrollmentId() + "/annual-upfront-requests").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"termsAccepted\":" + accepted + ",\"termsVersion\":\"" + version + "\"}");
        return mockMvc.perform(idempotencyKey == null ? builder : builder.header("Idempotency-Key", idempotencyKey)).andExpect(status().is(expectedStatus)).andReturn();
    }

    protected DiscountDefinition publishAnnualPromotion() {
        return publishAnnualPromotion(DiscountDraftBuilder.annualUpfront().withPercent("10").availableFrom(LocalDate.of(2027, 1, 1)));
    }

    protected DiscountDefinition publishAnnualPromotion(DiscountDraftBuilder draft) {
        DiscountDefinition saved = transactionTemplate.execute(status -> definitionRepository.saveAndFlush(DiscountDefinition.publishDiscountDefinition(draft.build(), director.getId(), Instant.now())));
        createdDefinitionIds.add(saved.getId());
        return saved;
    }

    protected long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
