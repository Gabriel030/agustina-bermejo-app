package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestStatus;
import com.red_escuela.backend.billing.authorization.repository.AuthorizationRequestRepository;
import com.red_escuela.backend.billing.authorization.usecase.ApproveAuthorizationRequestUseCase;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersionStatus;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.operation.AuthorizationPendingException;
import com.red_escuela.backend.billing.planning.TestTemplateTerms;
import com.red_escuela.backend.billing.dto.CreateProgramCohortRequest;
import com.red_escuela.backend.billing.usecase.cohort.CreateProgramCohortUseCase;
import com.red_escuela.backend.billing.usecase.cohort.UpdateCohortEntryFeeDiscountUseCase;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class VersionedCohortEntryDiscountIntegrationTest extends AbstractInvoiceSequenceTest {
    private static final Percentage TEN_PERCENT = Percentage.ofFraction(new BigDecimal("0.10"));
    private static final Percentage FIFTEEN_PERCENT = Percentage.ofFraction(new BigDecimal("0.15"));
    private static final LocalDate TODAY = LocalDate.of(2027, 1, 10);

    @Autowired
    private UpdateCohortEntryFeeDiscountUseCase updateEntryDiscount;
    @Autowired
    private CreateProgramCohortUseCase createCohort;
    @Autowired
    private ApproveAuthorizationRequestUseCase approveRequest;
    @Autowired
    private AuthorizationRequestRepository requestRepository;
    @Autowired
    private ConfigurationVersionRepository versionRepository;
    @Autowired
    private BillingConfigurationReader reader;
    @Autowired
    private RoleRepository roleRepository;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private Person personWithRole(String roleName) {
        return transactionTemplate.execute(status -> {
            Person person = newPerson();
            person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
            return person;
        });
    }

    private void authenticateAs(Person person, String roleName) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(person.getId().toString(), null, "ROLE_" + roleName));
    }

    private Long cohortWithDiscount(Percentage discount) {
        Career career = newCareer();
        Year year = newYear(career, 2027, 10, 2, 2, 7);
        Long cohortId = newCohort(year, CohortIntake.FIRST, LocalDate.of(2027, 3, 1));
        transactionTemplate.executeWithoutResult(status -> TestTemplateTerms.publishCohortEntryDiscount(entityManager, entityManager.find(ProgramCohort.class, cohortId), discount));
        return cohortId;
    }

    @Test
    @Tag("D-212")
    @Tag("D-095")
    void anAdministrativeEntryDiscountChangeStaysPendingUntilTheDirectorApprovesIt() {
        Long cohortId = cohortWithDiscount(TEN_PERCENT);
        today(TODAY);
        Person administrative = personWithRole("ADMINISTRATIVO");
        Person director = personWithRole("DIRECTOR");
        authenticateAs(administrative, "ADMINISTRATIVO");

        Long requestId = pendingRequestIdOf(cohortId);

        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus()).isEqualTo(AuthorizationRequestStatus.PENDING);
        assertThat(versionRepository.findFirstByAuthorizationRequestId(requestId).orElseThrow().getStatus()).isEqualTo(ConfigurationVersionStatus.PROPOSED);
        assertThat(reader.findCohortEntryEffectiveOn(cohortId, TODAY).orElseThrow().discount()).isEqualTo(TEN_PERCENT);

        authenticateAs(director, "DIRECTOR");
        approveRequest.approve(requestId, "de acuerdo");

        assertThat(versionRepository.findFirstByAuthorizationRequestId(requestId).orElseThrow().getStatus()).isEqualTo(ConfigurationVersionStatus.EFFECTIVE);
        assertThat(reader.findCohortEntryEffectiveOn(cohortId, TODAY).orElseThrow().discount()).isEqualTo(FIFTEEN_PERCENT);
    }

    @Test
    @Tag("D-212")
    @Tag("D-095")
    void aDirectorChangesTheEntryDiscountDirectlyWithoutAuthorizationRequest() {
        Long cohortId = cohortWithDiscount(TEN_PERCENT);
        today(TODAY);
        authenticateAs(personWithRole("DIRECTOR"), "DIRECTOR");

        updateEntryDiscount.update(cohortId, FIFTEEN_PERCENT);

        assertThat(reader.findCohortEntryEffectiveOn(cohortId, TODAY).orElseThrow().discount()).isEqualTo(FIFTEEN_PERCENT);
        assertThat(versionRepository.findAll().stream().filter(version -> cohortId.equals(version.getCohortId())).toList())
                .hasSize(2)
                .filteredOn(version -> version.getStatus() == ConfigurationVersionStatus.EFFECTIVE)
                .singleElement()
                .satisfies(version -> assertThat(version.isDirectPublication()).isTrue());
    }

    private static final Percentage THIRTY_PERCENT = Percentage.ofFraction(new BigDecimal("0.30"));
    private static final Percentage NONE = Percentage.ofFraction(BigDecimal.ZERO);

    private Long createCohortWithDiscount(Percentage discount) {
        Career career = newCareer();
        Year year = newYear(career, 2027, 10, 2, 2, 7);
        today(TODAY);
        LocalDate start = LocalDate.of(2027, 3, 1);
        return createCohort.create(year.templateId(), new CreateProgramCohortRequest(CohortIntake.FIRST, start.minusMonths(2), start.minusDays(1), start, discount)).getId();
    }

    private long versionsOf(Long cohortId) {
        return versionRepository.findAll().stream().filter(version -> cohortId.equals(version.getCohortId())).count();
    }

    private Long pendingRequestOf(Long cohortId) {
        return versionRepository.findAll().stream().filter(version -> cohortId.equals(version.getCohortId()) && version.getStatus() == ConfigurationVersionStatus.PROPOSED)
                .findFirst().orElseThrow().getAuthorizationRequestId();
    }

    @Test
    @Tag("D-234")
    @Tag("D-212")
    void anAdministrativeCreatingACohortWithANonDefaultDiscountLeavesItPendingUntilTheDirectorApprovesIt() {
        authenticateAs(personWithRole("ADMINISTRATIVO"), "ADMINISTRATIVO");

        Long cohortId = createCohortWithDiscount(THIRTY_PERCENT);

        Long requestId = pendingRequestOf(cohortId);
        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus()).isEqualTo(AuthorizationRequestStatus.PENDING);
        assertThat(reader.cohortEntryDiscountEffectiveOn(cohortId, TODAY)).isEqualTo(NONE);

        authenticateAs(personWithRole("DIRECTOR"), "DIRECTOR");
        approveRequest.approve(requestId, "de acuerdo");

        assertThat(reader.cohortEntryDiscountEffectiveOn(cohortId, TODAY)).isEqualTo(THIRTY_PERCENT);
    }

    @Test
    @Tag("D-234")
    @Tag("D-223")
    void aDirectorCreatingACohortWithANonDefaultDiscountPublishesItDirectly() {
        authenticateAs(personWithRole("DIRECTOR"), "DIRECTOR");

        Long cohortId = createCohortWithDiscount(THIRTY_PERCENT);

        assertThat(reader.cohortEntryDiscountEffectiveOn(cohortId, TODAY)).isEqualTo(THIRTY_PERCENT);
        assertThat(versionRepository.findAll().stream().filter(version -> cohortId.equals(version.getCohortId())))
                .singleElement().satisfies(version -> assertThat(version.isDirectPublication()).isTrue());
    }

    @Test
    @Tag("D-234")
    void anAdministrativeCreatingACohortWithTheDefaultDiscountOpensNoProposal() {
        authenticateAs(personWithRole("ADMINISTRATIVO"), "ADMINISTRATIVO");

        Long cohortId = createCohortWithDiscount(NONE);

        assertThat(versionsOf(cohortId)).isZero();
        assertThat(reader.cohortEntryDiscountEffectiveOn(cohortId, TODAY)).isEqualTo(NONE);
    }

    @Test
    @Tag("D-212")
    void anAdministrativeRepeatingTheCurrentDiscountOpensNoProposalAndCreatesNoVersion() {
        Long cohortId = cohortWithDiscount(TEN_PERCENT);
        today(TODAY);
        authenticateAs(personWithRole("ADMINISTRATIVO"), "ADMINISTRATIVO");
        long before = versionsOf(cohortId);

        updateEntryDiscount.update(cohortId, TEN_PERCENT);

        assertThat(versionsOf(cohortId)).isEqualTo(before);
    }

    private Long pendingRequestIdOf(Long cohortId) {
        AuthorizationPendingException pending = org.junit.jupiter.api.Assertions.assertThrows(AuthorizationPendingException.class,
                () -> updateEntryDiscount.update(cohortId, FIFTEEN_PERCENT));
        return pending.authorizationRequestId();
    }
}
