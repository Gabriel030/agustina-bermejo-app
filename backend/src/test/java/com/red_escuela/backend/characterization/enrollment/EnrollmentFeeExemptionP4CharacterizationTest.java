package com.red_escuela.backend.characterization.enrollment;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.planning.TestTemplateTerms;
import com.red_escuela.backend.billing.service.EnrollmentFeeRequirementService;
import com.red_escuela.backend.enrollment.dto.CreateSectionEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.EnrollmentFeeNotPaidForTermException;
import com.red_escuela.backend.enrollment.service.SubjectSectionEnrollmentService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EnrollmentFeeExemptionP4CharacterizationTest extends EnrollmentFeeP4Support {
    private static final String REASON = "Plan de pago acordado con el alumno";

    @Autowired
    private SubjectSectionEnrollmentService sectionEnrollmentService;
    @Autowired
    private EnrollmentFeeRequirementService requirementService;

    private Long onlyExemptionId(Long enrollmentId) {
        return transactionTemplate.execute(s -> entityManager.createQuery(
                "select e.id from EnrollmentFeeExemption e where e.programEnrollment.id = :id", Long.class)
                .setParameter("id", enrollmentId).getSingleResult());
    }

    private List<EnrollmentFeeRequirementService.Obligation> obligationsOf(Long enrollmentId) {
        return requirementService.statusOf(transactionTemplate.execute(s -> entityManager.find(ProgramEnrollment.class, enrollmentId)));
    }

    @Test
    void grantingAnExemptionRespondsWithTheFullResponseAndPersistsExactlyOneActiveRow() throws Exception {
        World world = worldWithFees(1, null);

        grantExemption(tokenWithRole("DIRECTOR"), world.enrollment(), world.year().templateId(), 1, "  " + REASON + "  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.templateId").value(world.year().templateId()))
                .andExpect(jsonPath("$.slot").value(1))
                .andExpect(jsonPath("$.reason").value(REASON))
                .andExpect(jsonPath("$.grantedByName").isString())
                .andExpect(jsonPath("$.grantedAt").isNotEmpty())
                .andExpect(jsonPath("$.revokedByName").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.revokedAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.revocationNotes").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.active").value(true));

        assertThat(countExemptions(world.enrollment())).isEqualTo(1);
        assertThat(enrollmentFeeInvoices(world.enrollment())).isEmpty();
    }

    @Test
    void grantedExemptionIsListedAndTurnsTheObligationIntoExemptedAndSatisfiedWithoutAnInvoice() throws Exception {
        World world = worldWithFees(2, 7);
        String director = tokenWithRole("DIRECTOR");
        grantExemption(director, world.enrollment(), world.year().templateId(), 1, REASON).andExpect(status().isCreated());

        exemptionList(tokenWithRole("PRECEPTOR"), world.enrollment())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slot").value(1))
                .andExpect(jsonPath("$[0].active").value(true));
        feeStatus(director, world.enrollment())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("EXEMPTED"))
                .andExpect(jsonPath("$[0].satisfied").value(true))
                .andExpect(jsonPath("$[0].invoiceId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].remainingBalance").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[1].status").value("NOT_ISSUED"))
                .andExpect(jsonPath("$[1].satisfied").value(false));
    }

    @Test
    void revokingAnExemptionKeepsTheRowAsInactiveAndTheObligationReturnsToNotIssued() throws Exception {
        World world = worldWithFees(1, null);
        String director = tokenWithRole("DIRECTOR");
        grantExemption(director, world.enrollment(), world.year().templateId(), 1, REASON).andExpect(status().isCreated());

        revokeExemption(director, onlyExemptionId(world.enrollment()), "Pago parcialmente").andExpect(status().isNoContent());

        exemptionList(director, world.enrollment())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[0].revocationNotes").value("Pago parcialmente"))
                .andExpect(jsonPath("$[0].revokedByName").isString())
                .andExpect(jsonPath("$[0].revokedAt").isNotEmpty());
        assertThat(obligationsOf(world.enrollment())).extracting(o -> o.status().name()).containsExactly("NOT_ISSUED");
        revokeExemption(director, onlyExemptionId(world.enrollment()), "otra vez").andExpect(status().isConflict());
        grantExemption(director, world.enrollment(), world.year().templateId(), 1, "Nueva habilitacion tras revocar").andExpect(status().isCreated());
        assertThat(countExemptions(world.enrollment())).isEqualTo(2);
    }

    @Test
    void grantingTwiceForTheSameSlotIsRejectedWithConflictAndKeepsTheFirstExemption() throws Exception {
        World world = worldWithFees(1, null);
        String director = tokenWithRole("DIRECTOR");
        grantExemption(director, world.enrollment(), world.year().templateId(), 1, REASON).andExpect(status().isCreated());

        grantExemption(director, world.enrollment(), world.year().templateId(), 1, "Segundo intento con otro motivo")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_FEE_EXEMPTION_ALREADY_EXISTS"));

        assertThat(countExemptions(world.enrollment())).isEqualTo(1);
        exemptionList(director, world.enrollment()).andExpect(jsonPath("$[0].reason").value(REASON));
    }

    @Test
    @Tag("D-110")
    void grantingASlotBeyondTheYearFeeCountIsRejectedAsInvalidConfigurationAndNothingIsStored() throws Exception {
        World world = worldWithFees(1, null);

        grantExemption(tokenWithRole("DIRECTOR"), world.enrollment(), world.year().templateId(), 2, REASON)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ENROLLMENT_FEE_CONFIGURATION"));

        assertThat(countExemptions(world.enrollment())).isZero();
    }

    @Test
    @Tag("D-110")
    void grantingWhenTheYearBlocksNothingIsRejectedAsNotNeededAndNothingIsStored() throws Exception {
        World world = worldWithFees(1, null);
        configureYear(world.year(), template -> {
            TestTemplateTerms.setBlocksEnrollment(template, false);
            TestTemplateTerms.setBlocksExamRegistration(template, false);
            TestTemplateTerms.setBlocksCertificates(template, false);
        });

        grantExemption(tokenWithRole("DIRECTOR"), world.enrollment(), world.year().templateId(), 1, REASON)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_FEE_EXEMPTION_NOT_NEEDED"));

        assertThat(countExemptions(world.enrollment())).isZero();
    }

    @Test
    void grantingWithUnknownEnrollmentUnknownTemplateOrTemplateOfAnotherProgramIsRejectedAndNothingIsStored() throws Exception {
        World world = worldWithFees(1, null);
        World otherProgram = worldWithFees(1, null);
        String director = tokenWithRole("DIRECTOR");

        grantExemption(director, 987_654_321L, world.year().templateId(), 1, REASON).andExpect(status().isNotFound());
        grantExemption(director, world.enrollment(), 987_654_321L, 1, REASON).andExpect(status().isNotFound());
        grantExemption(director, world.enrollment(), otherProgram.year().templateId(), 1, REASON).andExpect(status().isConflict());

        assertThat(countExemptions(world.enrollment())).isZero();
    }

    @Test
    void grantingEnforcesRolesAndRequestValidation() throws Exception {
        World world = worldWithFees(1, null);
        grantExemption(tokenWithRole("PRECEPTOR"), world.enrollment(), world.year().templateId(), 1, REASON).andExpect(status().isForbidden());
        grantExemption(tokenWithRole("ALUMNO"), world.enrollment(), world.year().templateId(), 1, REASON).andExpect(status().isForbidden());
        grantExemption(tokenWithRole("DIRECTOR"), world.enrollment(), world.year().templateId(), 1, "corto").andExpect(status().isBadRequest());
        grantExemption(tokenWithRole("DIRECTOR"), world.enrollment(), world.year().templateId(), 3, REASON).andExpect(status().isBadRequest());
        grantExemption(tokenWithRole("DIRECTOR"), world.enrollment(), world.year().templateId(), 0, REASON).andExpect(status().isBadRequest());

        assertThat(countExemptions(world.enrollment())).isZero();
        grantExemption(tokenWithRole("ADMINISTRATIVO"), world.enrollment(), world.year().templateId(), 1, REASON).andExpect(status().isCreated());
    }

    @Test
    void readingExemptionsAndStatusIsAllowedToPreceptorButNotToStudents() throws Exception {
        World world = worldWithFees(1, null);

        exemptionList(tokenWithRole("PRECEPTOR"), world.enrollment()).andExpect(status().isOk());
        feeStatus(tokenWithRole("PRECEPTOR"), world.enrollment()).andExpect(status().isOk());
        exemptionList(tokenWithRole("ALUMNO"), world.enrollment()).andExpect(status().isForbidden());
        feeStatus(tokenWithRole("ALUMNO"), world.enrollment()).andExpect(status().isForbidden());
        feeStatus(tokenWithRole("DIRECTOR"), 987_654_321L).andExpect(status().isNotFound());
    }

    @Test
    void obligationResponseFollowsTheInvoiceFromPendingToOverdueToPaid() throws Exception {
        World world = worldWithFees(1, null);
        String director = tokenWithRole("DIRECTOR");
        assertThatThrownBy(() -> sectionEnrollmentService.enroll(world.enrollment(), new CreateSectionEnrollmentRequest(world.firstSection())))
                .isInstanceOf(EnrollmentFeeNotPaidForTermException.class);
        Long invoiceId = enrollmentFeeInvoices(world.enrollment()).get(0).getId();

        feeStatus(director, world.enrollment())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].templateId").value(world.year().templateId()))
                .andExpect(jsonPath("$[0].slot").value(1))
                .andExpect(jsonPath("$[0].slotCount").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].invoiceId").value(invoiceId))
                .andExpect(jsonPath("$[0].dueDate").value("2027-03-10"))
                .andExpect(jsonPath("$[0].remainingBalance.amount").value("5000.00"))
                .andExpect(jsonPath("$[0].remainingBalance.currency").value("ARS"))
                .andExpect(jsonPath("$[0].satisfied").value(false));

        payAllEnrollmentFees(world.enrollment());
        assertThat(paymentStatusOf(invoiceId)).isEqualTo(InvoicePaymentStatus.PAID);
        feeStatus(director, world.enrollment())
                .andExpect(jsonPath("$[0].status").value("PAID"))
                .andExpect(jsonPath("$[0].satisfied").value(true))
                .andExpect(jsonPath("$[0].remainingBalance").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].invoiceId").value(invoiceId));
    }

    @Test
    void obligationStatusOmitsTheAdvanceFeeWhenTheNextYearHasNoTemplate() throws Exception {
        World world = worldWithFees(1, null);

        feeStatus(tokenWithRole("DIRECTOR"), world.enrollment())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].advance").doesNotExist());
        assertThat(obligationsOf(world.enrollment()).get(0).advance()).isNull();
    }

    @Test
    void studentReadsOnlyItsOwnFeeStatusFromMeAndAnotherEnrollmentIsNotFound() throws Exception {
        World world = worldWithFees(1, null);
        World other = worldWithFees(1, null);
        String ownerToken = studentToken(world.enrollment());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/me/program-enrollments/{id}/enrollment-fee-status", world.enrollment()).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("NOT_ISSUED"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/me/program-enrollments/{id}/enrollment-fee-status", other.enrollment()).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectedEnrollmentWithoutFeeKeepsTheOnDemandInvoiceButStoresNoSectionEnrollment() {
        World world = worldWithFees(1, null);

        assertThatThrownBy(() -> sectionEnrollmentService.enroll(world.enrollment(), new CreateSectionEnrollmentRequest(world.firstSection())))
                .isInstanceOf(EnrollmentFeeNotPaidForTermException.class);

        assertThat(enrollmentFeeInvoices(world.enrollment())).hasSize(1);
        Long storedSectionEnrollments = transactionTemplate.execute(s -> entityManager.createQuery(
                "select count(e) from SubjectSectionEnrollment e where e.programEnrollment.id = :id", Long.class)
                .setParameter("id", world.enrollment()).getSingleResult());
        assertThat(storedSectionEnrollments).isZero();
    }
}
