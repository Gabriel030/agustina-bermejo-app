package com.red_escuela.backend.characterization.enrollment;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateMismatchException;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateNotFoundException;
import com.red_escuela.backend.billing.service.ProgramEnrollmentBillingSetup;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProgramYearBillingTemplateCharacterizationTest extends CohortP3CharacterizationSupport {

    @Autowired
    private ProgramEnrollmentBillingSetup billingSetup;
    @Autowired
    private ProgramEnrollmentService programEnrollmentService;

    private ProgramEnrollment enrollmentInCohort(Program program, ProgramCohort cohort) {
        ProgramEnrollment enrollment = persistProgramEnrollment(persistStudent(), program, ProgramEnrollmentStatus.ENROLLED);
        enrollment.setProgramCohort(cohort);
        return programEnrollmentRepository.save(enrollment);
    }

    @Test
    void assigningTheTemplateIfMissingCopiesTheCohortTemplateToTheEnrollment() {
        Program program = persistProgram();
        ProgramCohort cohort = persistOpenCohortOf(program);
        ProgramEnrollment enrollment = enrollmentInCohort(program, cohort);

        billingSetup.assignTemplateIfMissing(enrollment);

        assertThat(storedTemplateIdOf(enrollment.getId())).isEqualTo(cohort.getTemplateId());
    }

    @Test
    void assigningTheTemplateIfMissingKeepsAnAlreadyAssignedTemplate() {
        Program program = persistProgram();
        ProgramCohort cohort = persistOpenCohortOf(program);
        ProgramYearBillingTemplate other = persistTemplate(program, today.getYear() + 1);
        ProgramEnrollment enrollment = enrollmentInCohort(program, cohort);
        enrollment.setCurrentBillingTemplate(other);
        programEnrollmentRepository.save(enrollment);

        billingSetup.assignTemplateIfMissing(enrollment);

        assertThat(storedTemplateIdOf(enrollment.getId())).isEqualTo(other.getId());
    }

    @Test
    void assigningTheTemplateIfMissingDoesNothingWhenTheEnrollmentHasNoCohort() {
        ProgramEnrollment enrollment = persistProgramEnrollment(persistStudent(), persistProgram(), ProgramEnrollmentStatus.ENROLLED);

        billingSetup.assignTemplateIfMissing(enrollment);

        assertThat(storedTemplateIdOf(enrollment.getId())).isNull();
    }

    @Test
    void advancingTheBillingYearReplacesTheTemplateOfTheEnrollmentAndKeepsTheCohort() {
        Program program = persistProgram();
        ProgramCohort cohort = persistOpenCohortOf(program);
        ProgramEnrollment enrollment = enrollmentInCohort(program, cohort);
        billingSetup.assignTemplateIfMissing(enrollment);
        ProgramYearBillingTemplate nextYear = persistTemplate(program, today.getYear() + 1);

        programEnrollmentService.advanceAcademicYear(enrollment.getId(), nextYear.getId());

        assertThat(storedTemplateIdOf(enrollment.getId())).isEqualTo(nextYear.getId());
        assertThat(storedCohortIdOf(enrollment.getId())).isEqualTo(cohort.getId());
    }

    @Test
    void advancingTheBillingYearWithATemplateOfAnotherProgramIsRejectedThroughHttpWithConflictAndKeepsTheTemplate() throws Exception {
        Program program = persistProgram();
        ProgramEnrollment enrollment = enrollmentInCohort(program, persistOpenCohortOf(program));
        billingSetup.assignTemplateIfMissing(enrollment);
        Long before = storedTemplateIdOf(enrollment.getId());
        ProgramYearBillingTemplate foreign = persistTemplate(persistProgram(), today.getYear() + 1);

        mockMvc.perform(post("/program-enrollments/" + enrollment.getId() + "/advance-billing-year").header("Authorization", bearerWithRole("ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"templateId\":" + foreign.getId() + "}"))
                .andExpect(status().isConflict());

        assertThat(storedTemplateIdOf(enrollment.getId())).isEqualTo(before);
        assertThatThrownBy(() -> programEnrollmentService.advanceAcademicYear(enrollment.getId(), foreign.getId()))
                .isInstanceOf(ProgramYearBillingTemplateMismatchException.class);
    }

    @Test
    void advancingTheBillingYearThroughHttpWithATemplateOfTheSameProgramReturnsTheEnrollment() throws Exception {
        Program program = persistProgram();
        ProgramEnrollment enrollment = enrollmentInCohort(program, persistOpenCohortOf(program));
        ProgramYearBillingTemplate nextYear = persistTemplate(program, today.getYear() + 1);

        mockMvc.perform(post("/program-enrollments/" + enrollment.getId() + "/advance-billing-year").header("Authorization", bearerWithRole("PRECEPTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"templateId\":" + nextYear.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(enrollment.getId()));

        assertThat(storedTemplateIdOf(enrollment.getId())).isEqualTo(nextYear.getId());
    }

    @Test
    void advancingTheBillingYearWithAnUnknownTemplateIsRejectedAsNotFound() {
        ProgramEnrollment enrollment = persistProgramEnrollment(persistStudent(), persistProgram(), ProgramEnrollmentStatus.ENROLLED);

        assertThatThrownBy(() -> programEnrollmentService.advanceAcademicYear(enrollment.getId(), 987_654_321L))
                .isInstanceOf(ProgramYearBillingTemplateNotFoundException.class);
    }
}
