package com.red_escuela.backend.characterization.enrollment;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.enrollment.exception.CohortAdmissionWindowClosedException;
import com.red_escuela.backend.billing.exception.ProgramCohortNotFoundException;
import com.red_escuela.backend.enrollment.dto.CreateProgramEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.exception.AlreadyEnrolledInProgramException;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.identity.entity.Student;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CohortAdmissionCharacterizationTest extends CohortP3CharacterizationSupport {

    @Autowired
    private ProgramEnrollmentService programEnrollmentService;

    private String enrollBody(Program program, Long cohortId) {
        return "{\"programId\":" + program.getId() + ",\"cohortId\":" + cohortId + "}";
    }

    private long enrollmentCountOf(Student student) {
        entityManager.flush();
        return jdbc.queryForObject("select count(*) from program_enrollment where student_id = ?", Long.class, student.getId());
    }

    @Test
    void enrollingThroughHttpInAnOpenCohortStoresTheCohortAndLeavesTheBillingTemplateUnassigned() throws Exception {
        Student student = persistStudent();
        Program program = persistProgram();
        ProgramCohort cohort = persistOpenCohortOf(program);

        String body = mockMvc.perform(post("/students/" + student.getId() + "/program-enrollments").header("Authorization", directorBearer())
                        .contentType(MediaType.APPLICATION_JSON).content(enrollBody(program, cohort.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ENROLLED"))
                .andReturn().getResponse().getContentAsString();

        Long enrollmentId = Long.valueOf(com.jayway.jsonpath.JsonPath.read(body, "$.id").toString());
        assertThat(storedCohortIdOf(enrollmentId)).isEqualTo(cohort.getId());
        assertThat(storedTemplateIdOf(enrollmentId)).isNull();
    }

    @Test
    void enrollingInACohortWhoseAdmissionEndedYesterdayIsRejectedWithConflictAndPersistsNothing() throws Exception {
        Student student = persistStudent();
        Program program = persistProgram();
        ProgramCohort closed = persistCohort(persistTemplate(program, today.getYear()), today.minusDays(30), today.minusDays(1));

        mockMvc.perform(post("/students/" + student.getId() + "/program-enrollments").header("Authorization", directorBearer())
                        .contentType(MediaType.APPLICATION_JSON).content(enrollBody(program, closed.getId())))
                .andExpect(status().isConflict());

        assertThat(enrollmentCountOf(student)).isZero();
    }

    @Test
    void enrollingInACohortWhoseAdmissionStartsTomorrowIsRejectedAsClosed() {
        Student student = persistStudent();
        Program program = persistProgram();
        ProgramCohort notYetOpen = persistCohort(persistTemplate(program, today.getYear()), today.plusDays(1), today.plusDays(30));

        assertThatThrownBy(() -> programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId(), notYetOpen.getId())))
                .isInstanceOf(CohortAdmissionWindowClosedException.class);
    }

    @Test
    void admissionWindowBoundariesAreInclusiveOnBothEnds() {
        Program program = persistProgram();
        ProgramCohort endsToday = persistCohort(persistTemplate(program, today.getYear()), today.minusDays(30), today);
        ProgramCohort startsToday = persistCohort(persistTemplate(program, today.getYear() + 1), today, today.plusDays(30));

        programEnrollmentService.validateAdmissionWindow(endsToday);
        programEnrollmentService.validateAdmissionWindow(startsToday);
    }

    @Test
    void cohortWithAdmissionStartAfterEndIsRejectedAsMisconfiguredWindow() {
        Program program = persistProgram();
        ProgramCohort inverted = persistCohort(persistTemplate(program, today.getYear()), today.plusDays(10), today.minusDays(10));

        assertThatThrownBy(() -> programEnrollmentService.validateAdmissionWindow(inverted))
                .isInstanceOf(com.red_escuela.backend.enrollment.api.InvalidWindowConfigurationException.class);
    }

    @Test
    void enrollingInAnUnknownCohortIsRejectedWithNotFound() throws Exception {
        Student student = persistStudent();
        Program program = persistProgram();

        mockMvc.perform(post("/students/" + student.getId() + "/program-enrollments").header("Authorization", directorBearer())
                        .contentType(MediaType.APPLICATION_JSON).content(enrollBody(program, 987_654_321L)))
                .andExpect(status().isNotFound());

        assertThatThrownBy(() -> programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId(), 987_654_321L)))
                .isInstanceOf(ProgramCohortNotFoundException.class);
    }

    @Test
    void enrollmentAfterAdmissionPaymentIgnoresTheClosedAdmissionWindow() {
        Student student = persistStudent();
        Program program = persistProgram();
        ProgramCohort closed = persistCohort(persistTemplate(program, today.getYear()), today.minusDays(60), today.minusDays(30));

        ProgramEnrollment created = programEnrollmentService.enrollAfterAdmissionPayment(student.getId(),
                new CreateProgramEnrollmentRequest(program.getId(), closed.getId()));

        assertThat(created.getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
        assertThat(storedCohortIdOf(created.getId())).isEqualTo(closed.getId());
    }

    @Test
    void enrollingAgainInTheSameProgramWhileActiveIsRejectedAndKeepsASingleEnrollment() {
        Student student = persistStudent();
        Program program = persistProgram();
        ProgramCohort cohort = persistOpenCohortOf(program);
        programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId(), cohort.getId()));

        assertThatThrownBy(() -> programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId(), cohort.getId())))
                .isInstanceOf(AlreadyEnrolledInProgramException.class);

        assertThat(enrollmentCountOf(student)).isEqualTo(1);
    }

    @Test
    void currentBehaviorToReviewEnrollingInAProgramWithACohortOfAnotherProgramIsAcceptedWithoutCheckingTheyMatch() {
        Student student = persistStudent();
        Program program = persistProgram();
        ProgramCohort cohortOfOtherProgram = persistOpenCohortOf(persistProgram());

        ProgramEnrollment created = programEnrollmentService.enroll(student.getId(),
                new CreateProgramEnrollmentRequest(program.getId(), cohortOfOtherProgram.getId()));

        assertThat(storedCohortIdOf(created.getId())).isEqualTo(cohortOfOtherProgram.getId());
        assertThat(created.getProgram().getId()).isEqualTo(program.getId());
        assertThat(cohortOfOtherProgram.getTemplate().getProgram().getId()).isNotEqualTo(program.getId());
    }

    @Test
    void enrollmentWithoutCohortSkipsTheAdmissionWindowCheckAndStoresNoCohort() {
        Student student = persistStudent();

        ProgramEnrollment created = programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(persistProgram().getId()));

        assertThat(storedCohortIdOf(created.getId())).isNull();
        assertThat(storedTemplateIdOf(created.getId())).isNull();
    }
}
