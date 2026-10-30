package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.testsupport.AsyncNotifications;
import com.red_escuela.backend.billing.planning.domain.EnrollmentFeeRequirementScope;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.enrollment.dto.ChangeSectionRequest;
import com.red_escuela.backend.enrollment.dto.CreateSectionEnrollmentRequest;
import com.red_escuela.backend.notification.entity.Notification;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.EnrollmentFeeNotPaidException;
import com.red_escuela.backend.enrollment.exception.EnrollmentFeeNotPaidForTermException;
import com.red_escuela.backend.enrollment.service.SubjectSectionEnrollmentService;
import com.red_escuela.backend.graduation.service.CertificateService;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.YearMonth;
import java.util.List;

import static com.red_escuela.backend.billing.support.InvoiceAssertions.assertThatInvoice;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class EnrollmentFeeGateTest extends AbstractInvoiceSequenceTest {
    @Autowired
    private SubjectSectionEnrollmentService enrollmentService;
    @Autowired
    private EnrollmentFeeRequirementService requirementService;
    @Autowired
    private CertificateService certificateService;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private record World(Career career, Year year, Term firstTerm, Term secondTerm, Long firstSection, Long secondSection, Long enrollment) {
    }

    private World world(int feeCount, Integer secondFeeMonth, boolean withCohort) {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, feeCount, 2, secondFeeMonth);
        Term first = newCuatrimestre(2027, 3, date(2027, 2, 1), date(2027, 3, 10), "1C");
        Term second = newCuatrimestre(2027, 8, date(2027, 7, 1), date(2027, 8, 10), "2C");
        Long firstSection = newSection(career, first);
        Long secondSection = newSection(career, second);
        today(date(2027, 2, 5));
        Long cohort = withCohort ? newCohort(y27, CohortIntake.FIRST, date(2027, 3, 1)) : null;
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 1), date(2027, 1, 20));
        return new World(career, y27, first, second, firstSection, secondSection, enrollment);
    }

    private void enrollIn(World world, Long sectionId) {
        enrollmentService.enroll(world.enrollment(), new CreateSectionEnrollmentRequest(sectionId));
    }

    private void leaveSection(World world, Long sectionId) {
        transactionTemplate.executeWithoutResult(s -> entityManager.createQuery(
                        "delete from SubjectSectionEnrollment e where e.programEnrollment.id = :id and e.subjectSection.id = :section")
                .setParameter("id", world.enrollment()).setParameter("section", sectionId).executeUpdate());
    }

    @Test
    @Tag("D-010")
    void withOneEnrollmentFeeThePaymentOfTheYearEnablesBothSemestersAndAnUnpaidOneBlocksWithTheNewCode() {
        World world = world(1, null, false);
        today(date(2027, 2, 5));

        assertThatThrownBy(() -> enrollIn(world, world.firstSection())).isInstanceOf(EnrollmentFeeNotPaidForTermException.class)
                .isInstanceOf(EnrollmentFeeNotPaidException.class);
        assertThat(periods(world.enrollment(), FeeType.ENROLLMENT_FEE)).containsExactly(YearMonth.of(2027, 2));

        markAllEnrollmentFees(world.enrollment(), InvoicePaymentStatus.PAID);
        enrollIn(world, world.firstSection());
        leaveSection(world, world.firstSection());
        today(date(2027, 7, 3));
        assertThatCode(() -> enrollIn(world, world.secondSection())).doesNotThrowAnyException();
    }

    @Test
    @Tag("D-010")
    void withTwoEnrollmentFeesEachSemesterNeedsItsOwnAndAnnualSubjectsNeedTheFirstOne() {
        World world = world(2, 7, false);
        today(date(2027, 2, 5));
        assertThatThrownBy(() -> enrollIn(world, world.firstSection())).isInstanceOf(EnrollmentFeeNotPaidForTermException.class);
        markInvoices(world.enrollment(), FeeType.ENROLLMENT_FEE, YearMonth.of(2027, 2), InvoicePaymentStatus.PAID);

        enrollIn(world, world.firstSection());
        leaveSection(world, world.firstSection());
        today(date(2027, 7, 3));
        assertThatThrownBy(() -> enrollIn(world, world.secondSection())).isInstanceOf(EnrollmentFeeNotPaidForTermException.class);
        markInvoices(world.enrollment(), FeeType.ENROLLMENT_FEE, YearMonth.of(2027, 7), InvoicePaymentStatus.PAID);
        assertThatCode(() -> enrollIn(world, world.secondSection())).doesNotThrowAnyException();
    }

    @Test
    @Tag("D-011")
    void anAnnualSubjectStartingInTheFirstSemesterOnlyNeedsTheFirstEnrollmentFee() {
        World world = world(2, 7, false);
        Term annual = newCuatrimestre(2027, 3, date(2027, 2, 1), date(2027, 3, 10), "ANUAL");
        Long annualSection = newSection(world.career(), annual);
        today(date(2027, 2, 5));
        assertThatThrownBy(() -> enrollIn(world, annualSection)).isInstanceOf(EnrollmentFeeNotPaidForTermException.class);
        markInvoices(world.enrollment(), FeeType.ENROLLMENT_FEE, YearMonth.of(2027, 2), InvoicePaymentStatus.PAID);

        assertThatCode(() -> enrollIn(world, annualSection)).doesNotThrowAnyException();
    }

    @Test
    @Tag("D-017")
    void theOnDemandEmissionHasTheWindowCloseAsDueDateAndTheEmissionDayPrice() {
        World world = world(1, null, false);
        newPrice(world.career(), FeeType.ENROLLMENT_FEE, 8000, date(2027, 1, 15));
        today(date(2027, 1, 20));

        assertThatThrownBy(() -> requirementService.requireForTerm(load(world.enrollment()), world.firstTerm()))
                .isInstanceOf(EnrollmentFeeNotPaidForTermException.class);

        Invoice fee = invoices(world.enrollment(), FeeType.ENROLLMENT_FEE).get(0);
        assertThat(fee.getDueDate()).isEqualTo(date(2027, 3, 10));
        assertThat(fee.getAmounts().linesTotal().value().amount().compareTo(java.math.BigDecimal.valueOf(8000))).isZero();
        assertThatInvoice(fee).isPending();
    }

    @Test
    void pendingAndOverdueBlockTheGate() {
        World world = world(1, null, false);
        today(date(2027, 2, 5));
        assertThatThrownBy(() -> enrollIn(world, world.firstSection())).isInstanceOf(EnrollmentFeeNotPaidForTermException.class);

        markInvoices(world.enrollment(), FeeType.ENROLLMENT_FEE, YearMonth.of(2027, 2), InvoicePaymentStatus.OVERDUE);
        assertThatThrownBy(() -> enrollIn(world, world.firstSection())).isInstanceOf(EnrollmentFeeNotPaidForTermException.class);
    }

    @Test
    void theEntryFeeCountsAsTheCycleFeeAndWithTwoFeesTheSecondSemesterEmitsOnDemand() {
        World world = world(2, 7, true);
        today(date(2027, 2, 5));
        issueEntryFee(world.enrollment());
        assertThatThrownBy(() -> enrollIn(world, world.firstSection())).isInstanceOf(EnrollmentFeeNotPaidForTermException.class);
        assertThat(periods(world.enrollment(), FeeType.ENROLLMENT_FEE)).containsExactly(YearMonth.of(2027, 3));
        markAllEnrollmentFees(world.enrollment(), InvoicePaymentStatus.PAID);

        enrollIn(world, world.firstSection());
        today(date(2027, 7, 3));
        assertThatThrownBy(() -> enrollIn(world, world.secondSection())).isInstanceOf(EnrollmentFeeNotPaidForTermException.class);
        assertThat(periods(world.enrollment(), FeeType.ENROLLMENT_FEE)).containsExactly(YearMonth.of(2027, 3), YearMonth.of(2027, 7));
    }

    @Test
    @Tag("D-013")
    void doesNotBlockWhenTheYearDoesNotBlockEnrollmentOrIsNotConfigured() {
        World world = world(1, null, false);
        configureYear(world.year(), template -> com.red_escuela.backend.billing.planning.TestTemplateTerms.setBlocksEnrollment(template, false));
        today(date(2027, 2, 5));
        assertThatCode(() -> enrollIn(world, world.firstSection())).doesNotThrowAnyException();

        Career bare = newCareer();
        Term term = newCuatrimestre(2030, 3, date(2030, 2, 1), date(2030, 3, 10), "1C");
        Long section = newSection(bare, term);
        today(date(2030, 2, 5));
        Long enrollment = newEnrollment(bare, null, PaymentPlanCadence.MONTHLY, YearMonth.of(2030, 1), date(2030, 1, 5));
        assertThatCode(() -> enrollmentService.enroll(enrollment, new CreateSectionEnrollmentRequest(section))).doesNotThrowAnyException();
        assertThat(invoices(enrollment, FeeType.ENROLLMENT_FEE)).isEmpty();
    }

    @Test
    @Tag("D-027")
    @Tag("D-166")
    void exemptionLetsTheStudentEnrollKeepsTheDebtAndRevokingItBlocksAgain() throws Exception {
        World world = world(1, null, false);
        today(date(2027, 2, 5));
        String director = tokenWithRole("DIRECTOR");

        grant(director, world, 1, "Plan de pago acordado con el alumno").andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.reason").value("Plan de pago acordado con el alumno"));
        enrollIn(world, world.firstSection());
        leaveSection(world, world.firstSection());
        today(date(2027, 7, 3));
        enrollIn(world, world.secondSection());
        assertThat(invoices(world.enrollment(), FeeType.ENROLLMENT_FEE)).isEmpty();

        grant(director, world, 1, "Segundo intento con otro motivo").andExpect(status().isConflict());
        Long exemptionId = firstExemptionId(world);
        mockMvc.perform(post("/enrollment-fee-exemptions/{id}/revoke", exemptionId).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + director).content("{\"notes\":\"Pagó parcialmente\"}")).andExpect(status().isNoContent());
        mockMvc.perform(post("/enrollment-fee-exemptions/{id}/revoke", exemptionId).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + director).content("{\"notes\":\"otra vez\"}")).andExpect(status().isConflict());

        leaveSection(world, world.secondSection());
        today(date(2027, 2, 6));
        assertThatThrownBy(() -> enrollIn(world, world.firstSection())).isInstanceOf(EnrollmentFeeNotPaidForTermException.class);
    }

    @Test
    void exemptionIsPerEnrollmentFeeSlotWhenTheYearHasTwo() throws Exception {
        World world = world(2, 7, false);
        today(date(2027, 2, 5));
        grant(tokenWithRole("ADMINISTRATIVO"), world, 1, "Habilitación administrativa por convenio").andExpect(status().isCreated());

        assertThatCode(() -> enrollIn(world, world.firstSection())).doesNotThrowAnyException();
        today(date(2027, 7, 3));
        assertThatThrownBy(() -> enrollIn(world, world.secondSection())).isInstanceOf(EnrollmentFeeNotPaidForTermException.class);
    }

    @Test
    @Tag("D-027")
    @Tag("D-110")
    void exemptionEndpointsEnforceRolesValidationAndTheNotNeededRule() throws Exception {
        World world = world(1, null, false);
        grant(tokenWithRole("PRECEPTOR"), world, 1, "Motivo suficientemente largo").andExpect(status().isForbidden());
        grant(tokenWithRole("ALUMNO"), world, 1, "Motivo suficientemente largo").andExpect(status().isForbidden());
        grant(tokenWithRole("DIRECTOR"), world, 1, "corto").andExpect(status().isBadRequest());
        grant(tokenWithRole("DIRECTOR"), world, 2, "Motivo suficientemente largo").andExpect(status().isBadRequest());
        mockMvc.perform(get("/program-enrollments/{id}/enrollment-fee-exemptions", world.enrollment())
                .header("Authorization", "Bearer " + tokenWithRole("PRECEPTOR"))).andExpect(status().isOk());
        mockMvc.perform(get("/program-enrollments/{id}/enrollment-fee-exemptions", world.enrollment())
                .header("Authorization", "Bearer " + tokenWithRole("ALUMNO"))).andExpect(status().isForbidden());

        configureYear(world.year(), template -> {
            com.red_escuela.backend.billing.planning.TestTemplateTerms.setBlocksEnrollment(template, false);
            com.red_escuela.backend.billing.planning.TestTemplateTerms.setBlocksExamRegistration(template, false);
            com.red_escuela.backend.billing.planning.TestTemplateTerms.setBlocksCertificates(template, false);
        });
        grant(tokenWithRole("DIRECTOR"), world, 1, "Motivo suficientemente largo").andExpect(status().isConflict());
    }

    @Test
    void statusEndpointDescribesEachObligationAndTheOwnerIsTheOnlyStudentThatCanReadItFromMe() throws Exception {
        World world = world(2, 7, false);
        World other = world(2, 7, false);
        today(date(2027, 2, 5));
        String ownerToken = studentToken(world.enrollment());
        mockMvc.perform(get("/me/program-enrollments/{id}/enrollment-fee-status", world.enrollment()).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].status").value("NOT_ISSUED"));
        mockMvc.perform(get("/me/program-enrollments/{id}/enrollment-fee-status", other.enrollment()).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());

        assertThat(requirementService.statusOf(load(world.enrollment()))).extracting(o -> o.status().name()).containsExactly("NOT_ISSUED", "NOT_ISSUED");
        assertThatThrownBy(() -> enrollIn(world, world.firstSection())).isInstanceOf(EnrollmentFeeNotPaidForTermException.class);
        assertThat(requirementService.statusOf(load(world.enrollment())).get(0).status().name()).isEqualTo("PENDING");
        markInvoices(world.enrollment(), FeeType.ENROLLMENT_FEE, YearMonth.of(2027, 2), InvoicePaymentStatus.PAID);
        assertThat(requirementService.statusOf(load(world.enrollment())).get(0).satisfied()).isTrue();
    }

    @Test
    @Tag("D-013")
    void examRegistrationAndCertificatesRequireTheLatestStartedEnrollmentFee() {
        World world = world(1, null, false);
        today(date(2027, 5, 10));
        authenticateAs(world.career());

        assertThatThrownBy(() -> requirementService.requireForCurrentPeriod(load(world.enrollment()), EnrollmentFeeRequirementScope.EXAM_REGISTRATION))
                .isInstanceOf(EnrollmentFeeNotPaidForTermException.class);
        assertThatThrownBy(() -> certificateService.issueApprovedSubjectsCertificate(world.enrollment()))
                .isInstanceOf(EnrollmentFeeNotPaidForTermException.class);

        markAllEnrollmentFees(world.enrollment(), InvoicePaymentStatus.PAID);
        assertThatCode(() -> requirementService.requireForCurrentPeriod(load(world.enrollment()), EnrollmentFeeRequirementScope.EXAM_REGISTRATION))
                .doesNotThrowAnyException();
        assertThat(certificateService.issueApprovedSubjectsCertificate(world.enrollment())).isNotEmpty();
    }

    @Test
    @Tag("D-013")
    void certificatesAndExamsOfTheYearThatDoesNotBlockThemAreAllowed() {
        World world = world(1, null, false);
        configureYear(world.year(), template -> {
            com.red_escuela.backend.billing.planning.TestTemplateTerms.setBlocksExamRegistration(template, false);
            com.red_escuela.backend.billing.planning.TestTemplateTerms.setBlocksCertificates(template, false);
        });
        today(date(2027, 5, 10));
        authenticateAs(world.career());

        assertThatCode(() -> requirementService.requireForCurrentPeriod(load(world.enrollment()), EnrollmentFeeRequirementScope.EXAM_REGISTRATION))
                .doesNotThrowAnyException();
        assertThat(certificateService.issueApprovedSubjectsCertificate(world.enrollment())).isNotEmpty();
    }

    @Test
    @Tag("D-016")
    void studentWhoOnlyOwesFinalsIsExemptFromTheExamGateWithoutOnDemandEmissionUnlessTheYearRequiresIt() {
        World world = world(1, null, false);
        addSectionEnrollment(world.enrollment(), world.career(), world.firstTerm(), EnrollmentStatus.REGULAR);
        today(date(2027, 5, 10));

        assertThatCode(() -> requirementService.requireForCurrentPeriod(load(world.enrollment()), EnrollmentFeeRequirementScope.EXAM_REGISTRATION))
                .doesNotThrowAnyException();
        assertThat(invoices(world.enrollment(), FeeType.ENROLLMENT_FEE)).isEmpty();

        configureYear(world.year(), template -> com.red_escuela.backend.billing.planning.TestTemplateTerms.setRequireEnrollmentFeeForFinalExamOnlyStudents(template, true));
        assertThatThrownBy(() -> requirementService.requireForCurrentPeriod(load(world.enrollment()), EnrollmentFeeRequirementScope.EXAM_REGISTRATION))
                .isInstanceOf(EnrollmentFeeNotPaidForTermException.class);
    }

    @Test
    void theGateIsNotEvaluatedForPreviousYearsWhenThereIsNoTemplateCoveringTheTerm() {
        World world = world(1, null, false);
        Term old = newCuatrimestre(2024, 3, date(2024, 2, 1), date(2024, 3, 10), "1C");
        Long oldSection = newSection(world.career(), old);
        today(date(2024, 2, 5));

        assertThatCode(() -> enrollIn(world, oldSection)).doesNotThrowAnyException();
    }

    @Test
    @Tag("D-028")
    void grantingAndRevokingAnExemptionNotifiesTheStudentTwice() throws Exception {
        World world = world(1, null, false);
        today(date(2027, 2, 5));
        String director = tokenWithRole("DIRECTOR");
        Long personId = transactionTemplate.execute(s -> load(world.enrollment()).getStudent().getPerson().getId());

        grant(director, world, 1, "Plan de pago acordado con el alumno").andExpect(status().isCreated());
        AsyncNotifications.awaitAsserted(() -> assertThat(notificationTypesOf(personId)).containsExactly(NotificationType.ENROLLMENT_FEE_EXEMPTION_GRANTED));

        mockMvc.perform(post("/enrollment-fee-exemptions/{id}/revoke", firstExemptionId(world)).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + director).content("{\"notes\":\"Pagó la matrícula\"}")).andExpect(status().isNoContent());

        AsyncNotifications.awaitAsserted(() -> assertThat(notificationTypesOf(personId)).containsExactlyInAnyOrder(
                NotificationType.ENROLLMENT_FEE_EXEMPTION_GRANTED, NotificationType.ENROLLMENT_FEE_EXEMPTION_REVOKED));
    }

    @Test
    @Tag("D-029")
    void changingSectionWithinTheSameTermDoesNotEvaluateTheEnrollmentFee() throws Exception {
        World world = world(1, null, false);
        today(date(2027, 2, 5));
        String director = tokenWithRole("DIRECTOR");
        grant(director, world, 1, "Plan de pago acordado con el alumno").andExpect(status().isCreated());
        Long enrollmentId = enrollmentService.enroll(world.enrollment(), new CreateSectionEnrollmentRequest(world.firstSection())).getId();
        mockMvc.perform(post("/enrollment-fee-exemptions/{id}/revoke", firstExemptionId(world)).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + director).content("{\"notes\":\"Todavía debe la matrícula\"}")).andExpect(status().isNoContent());
        Long otherSection = newSection(world.career(), world.firstTerm());

        assertThatCode(() -> enrollmentService.changeSection(enrollmentId, new ChangeSectionRequest(otherSection))).doesNotThrowAnyException();
        assertThat(invoices(world.enrollment(), FeeType.ENROLLMENT_FEE)).isEmpty();
    }

    private List<NotificationType> notificationTypesOf(Long personId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(personId).stream().map(Notification::getType).toList();
    }

    private ProgramEnrollment load(Long id) {
        return transactionTemplate.execute(s -> entityManager.find(ProgramEnrollment.class, id));
    }

    private void authenticateAs(Career career) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(career.directorId().toString(), null));
    }

    private ResultActions grant(String token, World world, int slot, String reason) throws Exception {
        return mockMvc.perform(post("/program-enrollments/{id}/enrollment-fee-exemptions", world.enrollment())
                .contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + token)
                .content("{\"templateId\":" + world.year().templateId() + ",\"slot\":" + slot + ",\"reason\":\"" + reason + "\"}"));
    }

    private Long firstExemptionId(World world) {
        return transactionTemplate.execute(s -> entityManager.createQuery(
                        "select e.id from EnrollmentFeeExemption e where e.programEnrollment.id = :id", Long.class)
                .setParameter("id", world.enrollment()).getSingleResult());
    }

    private String tokenWithRole(String roleName) {
        return transactionTemplate.execute(s -> {
            Person person = newPerson();
            person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
            entityManager.flush();
            return jwtService.generateAccessToken(person);
        });
    }

    private String studentToken(Long enrollmentId) {
        return transactionTemplate.execute(s -> {
            Person person = entityManager.find(ProgramEnrollment.class, enrollmentId).getStudent().getPerson();
            person.getRoles().add(roleRepository.findByName("ALUMNO").orElseThrow());
            entityManager.flush();
            return jwtService.generateAccessToken(person);
        });
    }
}
