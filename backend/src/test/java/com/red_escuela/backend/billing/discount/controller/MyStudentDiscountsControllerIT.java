package com.red_escuela.backend.billing.discount.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.operation.AbstractStudentDiscountIssuingIT;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.operation.IssuingScenarioFactory;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.enrollment.service.EnrollmentWithdrawal;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.StudentRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Tag("D-282")
class MyStudentDiscountsControllerIT extends AbstractStudentDiscountIssuingIT {

    private static final YearMonth MARCH = YearMonth.of(2027, 3);
    private static final YearMonth APRIL = YearMonth.of(2027, 4);
    private static final YearMonth MAY = YearMonth.of(2027, 5);
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
    @Autowired
    private ProgramEnrollmentService programEnrollmentService;

    private String studentToken(Long studentId) {
        return transactionTemplate.execute(status -> {
            Person person = studentRepository.findById(studentId).orElseThrow().getPerson();
            person.getRoles().add(roleRepository.findByName("ALUMNO").orElseThrow());
            personRepository.save(person);
            return jwtService.generateAccessToken(person);
        });
    }

    private String directorToken() {
        return transactionTemplate.execute(status -> jwtService.generateAccessToken(personRepository.findById(director.getId()).orElseThrow()));
    }

    private JsonNode read(String path, String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode discountNode(JsonNode list, StudentDiscount discount) {
        for (JsonNode node : list) {
            if (node.get("id").asLong() == discount.getId()) {
                return node;
            }
        }
        throw new AssertionError("The discount " + discount.getId() + " is not in " + list);
    }

    private List<String> texts(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        for (JsonNode element : array) {
            values.add(field == null ? element.asText() : element.get(field).asText());
        }
        return values;
    }

    @Test
    void aStudentSeesOnlyTheirOwnDiscounts() throws Exception {
        StudentDiscount own = grantAsDirector(scholarship("50", MARCH, JUNE));
        IssuingScenarioFactory.Learner other = newPlainLearner();
        grantAsDirectorTo(EnrollmentReference.ofProgram(other.programEnrollmentId()), scholarship("30", MARCH, JUNE));

        JsonNode mine = read("/me/student-discounts", studentToken(learner.studentId()));

        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("id").asLong()).isEqualTo(own.getId());
        assertThat(mine.get(0).get("programEnrollmentId").asLong()).isEqualTo(learner.programEnrollmentId());
        assertThat(mine.get(0).get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void anotherStudentsDiscountsAreNeverListed() throws Exception {
        grantAsDirector(scholarship("50", MARCH, JUNE));
        IssuingScenarioFactory.Learner other = newPlainLearner();
        EnrollmentReference otherEnrollment = EnrollmentReference.ofProgram(other.programEnrollmentId());
        StudentDiscount otherScholarship = grantAsDirectorTo(otherEnrollment, scholarship("30", MARCH, JUNE));
        StudentDiscount otherIndividual = grantAsDirectorTo(otherEnrollment, individual(InvoiceType.MONTHLY_INSTALLMENT, percent("10"), APRIL, APRIL, "Hermano"));
        IssuingScenarioFactory.Learner withoutDiscounts = newPlainLearner();

        List<String> ownIds = texts(read("/me/student-discounts", studentToken(learner.studentId())), "id");
        JsonNode nothing = read("/me/student-discounts", studentToken(withoutDiscounts.studentId()));
        List<String> otherIds = texts(read("/me/student-discounts", studentToken(other.studentId())), "id");

        assertThat(ownIds).doesNotContain(otherScholarship.getId().toString(), otherIndividual.getId().toString());
        assertThat(nothing).isEmpty();
        assertThat(otherIds).containsExactlyInAnyOrder(otherScholarship.getId().toString(), otherIndividual.getId().toString());
    }

    @Test
    void theReadingShowsNOfMAndTheLostMonths() throws Exception {
        StudentDiscount scholarship = grantAsDirector(scholarship("50", MARCH, JUNE));
        StudentDiscount aprilOnly = grantAsDirector(individual(InvoiceType.MONTHLY_INSTALLMENT, percent("60"), APRIL, APRIL, "Hermano en la institución"));
        for (YearMonth month : List.of(MARCH, APRIL, MAY, JUNE, JULY)) {
            issueInstallment(month);
        }

        JsonNode mine = read("/me/student-discounts", studentToken(learner.studentId()));

        JsonNode scholarshipNode = discountNode(mine, scholarship);
        assertThat(texts(scholarshipNode.get("appliedInstallments"), "period")).containsExactly("2027-03", "2027-05", "2027-06");
        assertThat(texts(scholarshipNode.get("appliedInstallments"), "installmentNumber")).containsExactly("1", "3", "4");
        assertThat(texts(scholarshipNode.get("appliedInstallments"), "installmentsTotal")).containsExactly("4", "4", "4");
        assertThat(texts(scholarshipNode.get("lostPeriods"), null)).containsExactly("2027-04");
        JsonNode individualNode = discountNode(mine, aprilOnly);
        assertThat(texts(individualNode.get("appliedInstallments"), "period")).containsExactly("2027-04");
        assertThat(individualNode.get("lostPeriods")).isEmpty();
    }

    @Test
    void theEnrollmentListingOfTheStaffShowsTheSameReading() throws Exception {
        StudentDiscount scholarship = grantAsDirector(scholarship("50", MARCH, JUNE));
        issueInstallment(MARCH);
        issueInstallment(APRIL);

        JsonNode listing = read("/program-enrollments/" + learner.programEnrollmentId() + "/student-discounts", directorToken());

        JsonNode node = discountNode(listing, scholarship);
        assertThat(texts(node.get("appliedInstallments"), "installmentNumber")).containsExactly("1", "2");
        assertThat(node.get("lostPeriods")).isEmpty();
    }

    @Test
    void aMonthCancelledByBillingErrorLeavesTheReadingAndComesBackWhenReissued() throws Exception {
        StudentDiscount scholarship = grantAsDirector(scholarship("50", MARCH, JUNE));
        Long march = issueInstallment(MARCH);
        String token = studentToken(learner.studentId());
        assertThat(texts(discountNode(read("/me/student-discounts", token), scholarship).get("appliedInstallments"), "period")).containsExactly("2027-03");

        cancelAsDirector(march, CreditNoteReason.BILLING_ERROR);

        JsonNode cancelled = discountNode(read("/me/student-discounts", token), scholarship);
        assertThat(cancelled.get("appliedInstallments")).isEmpty();
        assertThat(cancelled.get("lostPeriods")).isEmpty();

        issueInstallment(MARCH);

        JsonNode reissued = discountNode(read("/me/student-discounts", token), scholarship);
        assertThat(texts(reissued.get("appliedInstallments"), "period")).containsExactly("2027-03");
    }

    @Test
    void aVoluntaryWithdrawalEndsTheActiveDiscountsAndLeavesTheRevokedAndEndedOnesIntact() throws Exception {
        StudentDiscount revoked = grantAsDirector(scholarship("50", MARCH, MAY));
        revokeAsDirector(revoked.getId(), "Ya no corresponde");
        StudentDiscount active = grantAsDirector(scholarship("40", JULY, YearMonth.of(2027, 12)));
        StudentDiscount alreadyEnded = grantAsDirector(individual(InvoiceType.MONTHLY_INSTALLMENT, percent("10"), APRIL, APRIL, "Hermano"));
        jdbc.update("update student_discount set status = 'ENDED' where id = ?", alreadyEnded.getId());
        Integer historyOfEndedBefore = jdbc.queryForObject("select count(*) from student_discount_status_change where student_discount_id = ?", Integer.class, alreadyEnded.getId());

        programEnrollmentService.withdraw(learner.programEnrollmentId(), EnrollmentWithdrawal.voluntaryRegisteredBy(LocalDate.of(2027, 3, 1), director.getId()));

        JsonNode mine = read("/me/student-discounts", studentToken(learner.studentId()));
        assertThat(discountNode(mine, active).get("status").asText()).isEqualTo("ENDED");
        assertThat(discountNode(mine, revoked).get("status").asText()).isEqualTo("REVOKED");
        assertThat(discountNode(mine, alreadyEnded).get("status").asText()).isEqualTo("ENDED");
        assertThat(jdbc.queryForObject("select count(*) from student_discount_status_change where student_discount_id = ?", Integer.class, alreadyEnded.getId()))
                .isEqualTo(historyOfEndedBefore);
        assertThat(jdbc.queryForList("select event from student_discount_status_change where student_discount_id = ? order by id", String.class, revoked.getId()))
                .containsExactly("DISCOUNT_GRANTED", "DISCOUNT_REVOKED");
        assertThat(jdbc.queryForList("select event from student_discount_status_change where student_discount_id = ? order by id", String.class, active.getId()))
                .containsExactly("DISCOUNT_GRANTED", "ENROLLMENT_ENDED");
    }

    @Test
    @Tag("D-284")
    void aRevokedDiscountShowsTheStudentAGenericCauseAndNeverTheDirectorsReason() throws Exception {
        StudentDiscount revoked = grantAsDirector(scholarship("50", MARCH, MAY));
        revokeAsDirector(revoked.getId(), "Motivo interno confidencial del director");

        String raw = mockMvc.perform(get("/me/student-discounts").header("Authorization", "Bearer " + studentToken(learner.studentId()))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = discountNode(objectMapper.readTree(raw), revoked);

        assertThat(raw).doesNotContain("Motivo interno confidencial del director");
        assertThat(node.get("revocationReason").isNull()).isTrue();
        assertThat(node.get("status").asText()).isEqualTo("REVOKED");
        assertThat(node.get("endCause").asText()).isEqualTo("INSTITUTION_DECISION");
        assertThat(node.get("endCauseText").asText()).isEqualTo("Decisión de la institución");
        assertThat(node.get("revokedFromPeriod").asText()).isEqualTo("2027-03");
    }

    @Test
    @Tag("D-284")
    void theStaffKeepsSeeingTheRevocationReasonOfTheDirector() throws Exception {
        StudentDiscount revoked = grantAsDirector(scholarship("50", MARCH, MAY));
        revokeAsDirector(revoked.getId(), "Motivo interno del director");

        JsonNode node = discountNode(read("/program-enrollments/" + learner.programEnrollmentId() + "/student-discounts", directorToken()), revoked);

        assertThat(node.get("revocationReason").asText()).isEqualTo("Motivo interno del director");
        assertThat(node.get("endCause").asText()).isEqualTo("INSTITUTION_DECISION");
    }

    @Test
    @Tag("D-284")
    void aDiscountEndedByTheWithdrawalShowsTheEndOfTheEnrollmentAsCause() throws Exception {
        StudentDiscount active = grantAsDirector(scholarship("40", JULY, YearMonth.of(2027, 12)));
        StudentDiscount stays = grantAsDirectorTo(EnrollmentReference.ofProgram(newPlainLearner().programEnrollmentId()), scholarship("40", JULY, YearMonth.of(2027, 12)));

        programEnrollmentService.withdraw(learner.programEnrollmentId(), EnrollmentWithdrawal.voluntaryRegisteredBy(LocalDate.of(2027, 3, 1), director.getId()));

        JsonNode mine = read("/me/student-discounts", studentToken(learner.studentId()));
        JsonNode node = discountNode(mine, active);
        assertThat(node.get("status").asText()).isEqualTo("ENDED");
        assertThat(node.get("endCause").asText()).isEqualTo("ENROLLMENT_ENDED");
        assertThat(node.get("endCauseText").asText()).isEqualTo("Finalizó la inscripción");
        assertThat(jdbc.queryForObject("select status from student_discount where id = ?", String.class, stays.getId())).isEqualTo("ACTIVE");
    }

    @Test
    void anActiveDiscountHasNoEndCause() throws Exception {
        StudentDiscount active = grantAsDirector(scholarship("50", MARCH, JUNE));

        JsonNode node = discountNode(read("/me/student-discounts", studentToken(learner.studentId())), active);

        assertThat(node.get("endCause").isNull()).isTrue();
        assertThat(node.get("endCauseText").isNull()).isTrue();
    }

    @Test
    void theStudentSeesTheNamesOfTheEnrollmentAndOfTheDefinitionButNotTheirOwnName() throws Exception {
        StudentDiscount scholarship = grantAsDirector(scholarship("50", MARCH, JUNE));
        StudentDiscount individual = grantAsDirector(individual(InvoiceType.MONTHLY_INSTALLMENT, percent("10"), JULY, JULY, "Hermano"));
        String programName = jdbc.queryForObject("select p.name from program p join program_enrollment e on e.program_id = p.id where e.id = ?", String.class, learner.programEnrollmentId());
        String definitionName = jdbc.queryForObject("select name from discount_definition where id = ?", String.class, scholarship.getDiscountDefinitionId());

        JsonNode mine = read("/me/student-discounts", studentToken(learner.studentId()));

        assertThat(discountNode(mine, scholarship).get("enrollmentName").asText()).isEqualTo(programName);
        assertThat(discountNode(mine, scholarship).get("definitionName").asText()).isEqualTo(definitionName);
        assertThat(discountNode(mine, individual).get("enrollmentName").asText()).isEqualTo(programName);
        assertThat(discountNode(mine, individual).get("definitionName").isNull()).isTrue();
        assertThat(discountNode(mine, scholarship).get("studentName").isNull()).isTrue();
    }

    @Test
    void theLostBenefitCountEqualsTheLostMonths() throws Exception {
        StudentDiscount scholarship = grantAsDirector(scholarship("50", MARCH, JUNE));
        grantAsDirector(individual(InvoiceType.MONTHLY_INSTALLMENT, percent("60"), APRIL, APRIL, "Hermano en la institución"));
        for (YearMonth month : List.of(MARCH, APRIL, MAY, JUNE)) {
            issueInstallment(month);
        }

        JsonNode node = discountNode(read("/me/student-discounts", studentToken(learner.studentId())), scholarship);

        assertThat(node.get("lostPeriods")).hasSize(1);
        assertThat(node.get("lostBenefitCount").asInt()).isEqualTo(1);
    }
}
