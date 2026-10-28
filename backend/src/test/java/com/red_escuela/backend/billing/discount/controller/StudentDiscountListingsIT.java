package com.red_escuela.backend.billing.discount.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.operation.AbstractStudentDiscountIssuingIT;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.operation.IssuingScenarioFactory;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Tag("D-282")
@Tag("D-284")
class StudentDiscountListingsIT extends AbstractStudentDiscountIssuingIT {

    private static final YearMonth MARCH = YearMonth.of(2027, 3);
    private static final YearMonth APRIL = YearMonth.of(2027, 4);
    private static final YearMonth JUNE = YearMonth.of(2027, 6);
    private static final YearMonth JULY = YearMonth.of(2027, 7);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private StudentRepository studentRepository;

    private String tokenOf(Person person) {
        return transactionTemplate.execute(status -> jwtService.generateAccessToken(personRepository.findById(person.getId()).orElseThrow()));
    }

    private JsonNode get200(String path, String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode preview(EnrollmentReference target, String body, String token) throws Exception {
        String path = (target.kind().name().equals("PROGRAM") ? "/program-enrollments/" : "/course-enrollments/") + target.id() + "/student-discounts/preview";
        return objectMapper.readTree(mockMvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private List<Long> ids(JsonNode items) {
        List<Long> ids = new ArrayList<>();
        items.forEach(item -> ids.add(item.get("id").asLong()));
        return ids;
    }

    @Test
    void theGlobalListingFiltersByStatusAndEnrollment() throws Exception {
        StudentDiscount revoked = grantAsDirector(scholarship("50", MARCH, JUNE));
        revokeAsDirector(revoked.getId(), "Motivo interno del director");
        IssuingScenarioFactory.Learner other = newPlainLearner();
        StudentDiscount active = grantAsDirectorTo(EnrollmentReference.ofProgram(other.programEnrollmentId()), scholarship("30", MARCH, JUNE));
        String token = tokenOf(director);

        JsonNode revokedOfFirst = get200("/student-discounts?status=REVOKED&programEnrollmentId=" + learner.programEnrollmentId(), token).get("items");
        JsonNode activeOfFirst = get200("/student-discounts?status=ACTIVE&programEnrollmentId=" + learner.programEnrollmentId(), token).get("items");
        JsonNode activeOfOther = get200("/student-discounts?status=ACTIVE&programEnrollmentId=" + other.programEnrollmentId(), token).get("items");

        assertThat(ids(revokedOfFirst)).containsExactly(revoked.getId());
        assertThat(revokedOfFirst.get(0).get("revocationReason").asText()).isEqualTo("Motivo interno del director");
        assertThat(activeOfFirst).isEmpty();
        assertThat(ids(activeOfOther)).containsExactly(active.getId());
    }

    @Test
    void theGlobalListingWithoutFiltersShowsTheNewestFirstAcrossStudents() throws Exception {
        StudentDiscount first = grantAsDirector(scholarship("50", MARCH, JUNE));
        IssuingScenarioFactory.Learner other = newPlainLearner();
        StudentDiscount second = grantAsDirectorTo(EnrollmentReference.ofProgram(other.programEnrollmentId()), scholarship("30", MARCH, JUNE));

        JsonNode page = get200("/student-discounts?size=100", tokenOf(administrative));

        List<Long> ids = ids(page.get("items"));
        assertThat(ids).contains(first.getId(), second.getId());
        assertThat(ids.indexOf(second.getId())).isLessThan(ids.indexOf(first.getId()));
        assertThat(page.get("totalElements").asLong()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void thePaginationReportsTheTotalAndKeepsTheNewestFirst() throws Exception {
        StudentDiscount scholarship = grantAsDirector(scholarship("50", MARCH, MARCH));
        StudentDiscount monthly = grantAsDirector(individual(InvoiceType.MONTHLY_INSTALLMENT, percent("10"), APRIL, APRIL, "Hermano"));
        StudentDiscount onFee = grantAsDirector(individual(InvoiceType.ENROLLMENT_FEE, percent("10"), APRIL, APRIL, "Hermano"));
        String base = "/student-discounts?studentId=" + learner.studentId();
        String token = tokenOf(director);

        JsonNode firstPage = get200(base + "&page=0&size=2", token);
        JsonNode secondPage = get200(base + "&page=1&size=2", token);
        JsonNode beyond = get200(base + "&page=5&size=2", token);

        assertThat(firstPage.get("totalElements").asLong()).isEqualTo(3);
        assertThat(ids(firstPage.get("items"))).containsExactly(onFee.getId(), monthly.getId());
        assertThat(ids(secondPage.get("items"))).containsExactly(scholarship.getId());
        assertThat(secondPage.get("page").asInt()).isEqualTo(1);
        assertThat(beyond.get("items")).isEmpty();
        assertThat(beyond.get("totalElements").asLong()).isEqualTo(3);
    }

    @Test
    void thePageSizeIsBoundedBetweenOneAndOneHundred() throws Exception {
        grantAsDirector(scholarship("50", MARCH, JUNE));
        String token = tokenOf(director);

        assertThat(get200("/student-discounts?size=1000", token).get("size").asInt()).isEqualTo(100);
        JsonNode tiny = get200("/student-discounts?studentId=" + learner.studentId() + "&size=0", token);
        assertThat(tiny.get("size").asInt()).isEqualTo(1);
        assertThat(tiny.get("items")).hasSize(1);
        assertThat(get200("/student-discounts?page=-3", token).get("page").asInt()).isZero();
    }

    @Test
    void theListingFiltersByTheTypeOfTheDiscountAndByItsDefinition() throws Exception {
        StudentDiscount scholarship = grantAsDirector(scholarship("50", MARCH, JUNE));
        StudentDiscount individual = grantAsDirector(individual(InvoiceType.MONTHLY_INSTALLMENT, percent("10"), JULY, JULY, "Hermano"));
        String base = "/student-discounts?studentId=" + learner.studentId();
        String token = tokenOf(director);

        assertThat(ids(get200(base + "&definitionType=SCHOLARSHIP", token).get("items"))).containsExactly(scholarship.getId());
        assertThat(get200(base + "&definitionType=COHORT_PROMOTION", token).get("items")).isEmpty();
        assertThat(ids(get200(base + "&type=INDIVIDUAL", token).get("items"))).containsExactly(individual.getId());
        assertThat(ids(get200(base + "&discountDefinitionId=" + scholarship.getDiscountDefinitionId(), token).get("items"))).containsExactly(scholarship.getId());
        assertThat(get200(base + "&discountDefinitionId=-1", token).get("items")).isEmpty();
    }

    @Test
    void everyItemCarriesTheNamesOfTheStudentTheEnrollmentAndTheDefinition() throws Exception {
        StudentDiscount scholarship = grantAsDirector(scholarship("50", MARCH, JUNE));
        StudentDiscount individual = grantAsDirector(individual(InvoiceType.MONTHLY_INSTALLMENT, percent("10"), JULY, JULY, "Hermano"));
        String programName = jdbc.queryForObject("select p.name from program p join program_enrollment e on e.program_id = p.id where e.id = ?", String.class, learner.programEnrollmentId());
        String definitionName = jdbc.queryForObject("select name from discount_definition where id = ?", String.class, scholarship.getDiscountDefinitionId());
        String studentName = transactionTemplate.execute(status -> {
            Person person = studentRepository.findById(learner.studentId()).orElseThrow().getPerson();
            return person.getName() + " " + person.getLastname();
        });

        JsonNode items = get200("/student-discounts?studentId=" + learner.studentId(), tokenOf(director)).get("items");

        JsonNode scholarshipNode = items.get(1);
        JsonNode individualNode = items.get(0);
        assertThat(scholarshipNode.get("id").asLong()).isEqualTo(scholarship.getId());
        assertThat(scholarshipNode.get("enrollmentName").asText()).isEqualTo(programName);
        assertThat(scholarshipNode.get("definitionName").asText()).isEqualTo(definitionName);
        assertThat(scholarshipNode.get("definitionType").asText()).isEqualTo("SCHOLARSHIP");
        assertThat(scholarshipNode.get("studentName").asText()).isEqualTo(studentName);
        assertThat(scholarshipNode.get("studentId").asLong()).isEqualTo(learner.studentId());
        assertThat(individualNode.get("id").asLong()).isEqualTo(individual.getId());
        assertThat(individualNode.get("definitionName").isNull()).isTrue();
        assertThat(individualNode.get("definitionType").isNull()).isTrue();
        assertThat(individualNode.get("enrollmentName").asText()).isEqualTo(programName);
    }

    @Test
    void theListingOfAStudentCoversAllTheirEnrollmentsAndNeverAnotherStudents() throws Exception {
        StudentDiscount own = grantAsDirector(scholarship("50", MARCH, JUNE));
        IssuingScenarioFactory.Learner other = newPlainLearner();
        StudentDiscount foreign = grantAsDirectorTo(EnrollmentReference.ofProgram(other.programEnrollmentId()), scholarship("30", MARCH, JUNE));

        JsonNode ofStudent = get200("/students/" + learner.studentId() + "/student-discounts", tokenOf(administrative));

        assertThat(ids(ofStudent)).containsExactly(own.getId()).doesNotContain(foreign.getId());
        assertThat(ofStudent.get(0).get("studentName").asText()).isNotBlank();
        mockMvc.perform(get("/students/-1/student-discounts").header("Authorization", "Bearer " + tokenOf(director))).andExpect(status().isNotFound());
    }

    @Test
    void thePreviewOfAValidGrantReportsTheImpactAndPersistsNothing() throws Exception {
        long before = jdbc.queryForObject("select count(*) from student_discount where student_id = ?", Long.class, learner.studentId());

        JsonNode preview = preview(enrollment(), """
                {"type":"SCHOLARSHIP","percent":50,"fromPeriod":"2027-03","toPeriod":"2027-06","reason":"Situación económica"}
                """, tokenOf(director));

        assertThat(preview.get("allowed").asBoolean()).isTrue();
        assertThat(preview.get("violations")).isEmpty();
        assertThat(preview.get("fromPeriod").asText()).isEqualTo("2027-03");
        assertThat(preview.get("nextInstallmentPeriod").asText()).isEqualTo("2027-03");
        assertThat(preview.get("coveredPeriods").asInt()).isEqualTo(4);
        assertThat(preview.get("requiresDirectorApproval").asBoolean()).isFalse();
        assertThat(preview.get("discountDefinitionId").asLong()).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from student_discount where student_id = ?", Long.class, learner.studentId())).isEqualTo(before);
    }

    @Test
    void thePreviewAnswersTheSameViolationTheRealGrantRejectsWith() throws Exception {
        grantAsDirector(scholarship("50", APRIL, JUNE));
        String overlappingBody = """
                {"type":"SCHOLARSHIP","percent":30,"fromPeriod":"2027-05","toPeriod":"2027-08","reason":"Situación económica"}
                """;

        JsonNode overlapping = preview(enrollment(), overlappingBody, tokenOf(director));
        JsonNode beforeNext = preview(enrollment(), """
                {"type":"SCHOLARSHIP","percent":30,"fromPeriod":"2027-02","toPeriod":"2027-08","reason":"Situación económica"}
                """, tokenOf(director));

        assertThat(overlapping.get("allowed").asBoolean()).isFalse();
        assertThat(overlapping.get("violations").get(0).get("code").asText()).isEqualTo("STUDENT_DISCOUNT_OVERLAP");
        assertThat(overlapping.get("overlappingDiscountId").asLong()).isPositive();
        assertThat(beforeNext.get("violations").get(0).get("code").asText()).isEqualTo("SCHOLARSHIP_START_BEFORE_NEXT_INSTALLMENT");
        mockMvc.perform(post("/program-enrollments/" + learner.programEnrollmentId() + "/student-discounts").header("Authorization", "Bearer " + tokenOf(director))
                .contentType(MediaType.APPLICATION_JSON).content(overlappingBody)).andExpect(status().isConflict());
    }

    @Test
    void thePreviewOfAnAdministrativeUserSaysTheDirectorMustApprove() throws Exception {
        JsonNode preview = preview(enrollment(), """
                {"type":"INDIVIDUAL","percent":10,"invoiceType":"MONTHLY_INSTALLMENT","fromPeriod":"2027-05","toPeriod":"2027-05","reason":"Hermano"}
                """, tokenOf(administrative));

        assertThat(preview.get("allowed").asBoolean()).isTrue();
        assertThat(preview.get("requiresDirectorApproval").asBoolean()).isTrue();
        assertThat(preview.get("discountDefinitionId").isNull()).isTrue();
    }

    @Test
    void thePreviewOfAnUnknownEnrollmentAnswersNotFound() throws Exception {
        mockMvc.perform(post("/program-enrollments/-1/student-discounts/preview").header("Authorization", "Bearer " + tokenOf(director)).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type":"INDIVIDUAL","percent":10,"fromPeriod":"2027-05","toPeriod":"2027-05","reason":"Hermano"}
                        """)).andExpect(status().isNotFound());
    }
}
