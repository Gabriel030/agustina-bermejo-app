package com.red_escuela.backend.characterization.enrollment;

import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.planning.TestTemplateTerms;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExamBoardEnrollmentFeeP9CharacterizationTest extends EnrollmentFeeP4Support {

    private record Registrable(World world, Long sectionEnrollmentId, Long examBoardId) {
    }

    private Registrable registrable(int feeCount, Integer secondFeeMonth, EnrollmentStatus status) {
        World world = worldWithFees(feeCount, secondFeeMonth);
        Long sectionEnrollmentId = newSectionEnrollment(world.enrollment(), world.firstSection(), status);
        Long examBoardId = newExamBoard(world.career().subjectId(), world.firstTerm());
        return new Registrable(world, sectionEnrollmentId, examBoardId);
    }

    private Long newExamBoard(Long subjectId, Term term) {
        return transactionTemplate.execute(s -> {
            ExamBoard board = new ExamBoard();
            board.setSubject(entityManager.getReference(Subject.class, subjectId));
            board.setTerm(entityManager.getReference(Term.class, term.getId()));
            board.setExamDate(LocalDateTime.of(2027, 7, 20, 10, 0));
            board.setStatus(ExamBoardStatus.SCHEDULED);
            entityManager.persist(board);
            return board.getId();
        });
    }

    private void requireFeeEvenForStudentsWhoOnlyOweFinals(Registrable scenario) {
        configureYear(scenario.world().year(), template -> TestTemplateTerms.setRequireEnrollmentFeeForFinalExamOnlyStudents(template, true));
    }

    private org.springframework.test.web.servlet.ResultActions registerAsStaff(Registrable scenario) throws Exception {
        return mockMvc.perform(post("/exam-boards/{id}/registrations", scenario.examBoardId()).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + tokenWithRole("PRECEPTOR"))
                .content("{\"examBoardId\":" + scenario.examBoardId() + ",\"programSectionEnrollmentId\":" + scenario.sectionEnrollmentId() + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions registerAsStudent(Registrable scenario) throws Exception {
        return mockMvc.perform(post("/me/exam-board-registrations").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + studentToken(scenario.world().enrollment()))
                .content("{\"examBoardId\":" + scenario.examBoardId() + ",\"programSectionEnrollmentId\":" + scenario.sectionEnrollmentId() + "}"));
    }

    private long registrationsOf(Registrable scenario) {
        return transactionTemplate.execute(s -> entityManager.createQuery(
                "select count(r) from ExamBoardRegistration r where r.examBoard.id = :id", Long.class)
                .setParameter("id", scenario.examBoardId()).getSingleResult());
    }

    @Test
    @Tag("D-013")
    void registeringWithTheFeeRequiredAndUnpaidIsRejectedKeepsTheOnDemandInvoiceAndStoresNoRegistration() throws Exception {
        Registrable scenario = registrable(1, null, EnrollmentStatus.REGULAR);
        requireFeeEvenForStudentsWhoOnlyOweFinals(scenario);

        registerAsStaff(scenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_FEE_NOT_PAID_FOR_TERM"));

        assertThat(registrationsOf(scenario)).isZero();
        assertThat(enrollmentFeeInvoices(scenario.world().enrollment())).hasSize(1);
    }

    @Test
    @Tag("D-013")
    void registeringWithTheFeePaidCreatesTheRegisteredRegistration() throws Exception {
        Registrable scenario = registrable(1, null, EnrollmentStatus.REGULAR);
        requireFeeEvenForStudentsWhoOnlyOweFinals(scenario);
        registerAsStaff(scenario).andExpect(status().isBadRequest());
        payAllEnrollmentFees(scenario.world().enrollment());

        registerAsStaff(scenario)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REGISTERED"));

        assertThat(registrationsOf(scenario)).isEqualTo(1);
        assertThat(enrollmentFeeInvoices(scenario.world().enrollment())).hasSize(1);
    }

    @Test
    @Tag("D-027")
    void registeringWithAnActiveExemptionSucceedsWithoutIssuingAnInvoiceAndRevokingItBlocksAgain() throws Exception {
        Registrable scenario = registrable(1, null, EnrollmentStatus.REGULAR);
        requireFeeEvenForStudentsWhoOnlyOweFinals(scenario);
        String director = tokenWithRole("DIRECTOR");
        grantExemption(director, scenario.world().enrollment(), scenario.world().year().templateId(), 1, "Plan de pago acordado con el alumno")
                .andExpect(status().isCreated());

        registerAsStaff(scenario).andExpect(status().isCreated());

        assertThat(registrationsOf(scenario)).isEqualTo(1);
        assertThat(enrollmentFeeInvoices(scenario.world().enrollment())).isEmpty();
    }

    @Test
    @Tag("D-027")
    void revokedExemptionBlocksTheRegistrationAgain() throws Exception {
        Registrable scenario = registrable(1, null, EnrollmentStatus.REGULAR);
        requireFeeEvenForStudentsWhoOnlyOweFinals(scenario);
        String director = tokenWithRole("DIRECTOR");
        grantExemption(director, scenario.world().enrollment(), scenario.world().year().templateId(), 1, "Plan de pago acordado con el alumno")
                .andExpect(status().isCreated());
        Long exemptionId = transactionTemplate.execute(s -> entityManager.createQuery(
                "select e.id from EnrollmentFeeExemption e where e.programEnrollment.id = :id", Long.class)
                .setParameter("id", scenario.world().enrollment()).getSingleResult());
        revokeExemption(director, exemptionId, "Todavia debe la matricula").andExpect(status().isNoContent());

        registerAsStaff(scenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_FEE_NOT_PAID_FOR_TERM"));

        assertThat(registrationsOf(scenario)).isZero();
    }

    @Test
    @Tag("D-016")
    void studentWhoOnlyOwesFinalsRegistersWithoutPayingAndWithoutAnInvoiceUnlessTheYearRequiresIt() throws Exception {
        Registrable scenario = registrable(1, null, EnrollmentStatus.REGULAR);

        registerAsStaff(scenario).andExpect(status().isCreated());

        assertThat(registrationsOf(scenario)).isEqualTo(1);
        assertThat(enrollmentFeeInvoices(scenario.world().enrollment())).isEmpty();
    }

    @Test
    @Tag("D-013")
    void registeringIsAllowedWhenTheYearDoesNotBlockExamRegistrationEvenWithTheFeeUnpaid() throws Exception {
        Registrable scenario = registrable(1, null, EnrollmentStatus.REGULAR);
        requireFeeEvenForStudentsWhoOnlyOweFinals(scenario);
        configureYear(scenario.world().year(), template -> TestTemplateTerms.setBlocksExamRegistration(template, false));

        registerAsStaff(scenario).andExpect(status().isCreated());

        assertThat(registrationsOf(scenario)).isEqualTo(1);
        assertThat(enrollmentFeeInvoices(scenario.world().enrollment())).isEmpty();
    }

    @Test
    void studentSelfRegistrationFromMeAppliesTheSameFeeGate() throws Exception {
        Registrable scenario = registrable(1, null, EnrollmentStatus.REGULAR);
        requireFeeEvenForStudentsWhoOnlyOweFinals(scenario);

        registerAsStudent(scenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_FEE_NOT_PAID_FOR_TERM"));
        assertThat(registrationsOf(scenario)).isZero();

        payAllEnrollmentFees(scenario.world().enrollment());
        registerAsStudent(scenario).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("REGISTERED"));
        assertThat(registrationsOf(scenario)).isEqualTo(1);
    }

    @Test
    void feeCheckRunsBeforeTheEligibilityCheckSoAnUnpaidAndNonRegularStudentSeesTheFeeError() throws Exception {
        Registrable scenario = registrable(1, null, EnrollmentStatus.IN_PROGRESS);

        registerAsStaff(scenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_FEE_NOT_PAID_FOR_TERM"));

        payAllEnrollmentFees(scenario.world().enrollment());
        registerAsStaff(scenario).andExpect(status().is4xxClientError()).andExpect(jsonPath("$.code").value("STUDENT_NOT_REGULAR"));
        assertThat(registrationsOf(scenario)).isZero();
    }

    @Test
    void withTwoFeesTheExamOfTheSecondSemesterNeedsTheSecondFee() throws Exception {
        World world = worldWithFees(2, 7);
        Long sectionEnrollmentId = newSectionEnrollment(world.enrollment(), world.secondSection(), EnrollmentStatus.IN_PROGRESS);
        Long examBoardId = newExamBoard(world.career().subjectId(), world.secondTerm());
        Registrable scenario = new Registrable(world, sectionEnrollmentId, examBoardId);
        today(date(2027, 7, 3));

        registerAsStaff(scenario)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_FEE_NOT_PAID_FOR_TERM"));

        assertThat(enrollmentFeeInvoices(world.enrollment())).extracting(invoice -> invoice.getPeriod().toString()).containsExactly("2027-07");
        assertThat(registrationsOf(scenario)).isZero();
    }

    @Test
    @Tag("D-013")
    void certificateIssuingRequiresTheLatestStartedFeeAndIssuesOnDemandWhenUnpaid() throws Exception {
        World world = worldWithFees(1, null);
        today(date(2027, 5, 10));
        String student = studentToken(world.enrollment());

        mockMvc.perform(get("/me/program-enrollments/{id}/certificates/approved-subjects", world.enrollment()).header("Authorization", "Bearer " + student))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_FEE_NOT_PAID_FOR_TERM"));
        assertThat(enrollmentFeeInvoices(world.enrollment())).hasSize(1);

        payAllEnrollmentFees(world.enrollment());
        mockMvc.perform(get("/me/program-enrollments/{id}/certificates/approved-subjects", world.enrollment()).header("Authorization", "Bearer " + student))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", "application/pdf"));
    }

    @Test
    @Tag("D-027")
    void certificateIssuingWithAnActiveExemptionSucceedsWithoutAnInvoice() throws Exception {
        World world = worldWithFees(1, null);
        today(date(2027, 5, 10));
        grantExemption(tokenWithRole("DIRECTOR"), world.enrollment(), world.year().templateId(), 1, "Plan de pago acordado con el alumno")
                .andExpect(status().isCreated());

        mockMvc.perform(get("/me/program-enrollments/{id}/certificates/approved-subjects", world.enrollment())
                        .header("Authorization", "Bearer " + studentToken(world.enrollment())))
                .andExpect(status().isOk());

        assertThat(enrollmentFeeInvoices(world.enrollment())).isEmpty();
    }
}
