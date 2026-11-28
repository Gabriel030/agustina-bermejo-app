package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.billing.dto.YearBillingConfigurationRequest;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.usecase.cohort.CreateProgramYearBillingTemplateUseCase;
import com.red_escuela.backend.billing.usecase.cohort.UpdateYearBillingConfigurationUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.AtomicityTestSupport;
import com.red_escuela.backend.testsupport.TermFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
class YearBillingTransactionBoundaryIT extends AbstractIntegrationTest {

    @Autowired
    private CreateProgramYearBillingTemplateUseCase createTemplate;

    @Autowired
    private UpdateYearBillingConfigurationUseCase updateConfiguration;

    @Autowired
    private ConfigurationVersionActivator activator;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private TermRepository termRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Program program;
    private Term term;

    @BeforeEach
    void seedDirectorProgramAndTerm() {
        String username = "year.billing." + System.nanoTime();
        Person director = new Person();
        director.setUsername(username);
        director.setDni(Long.toString(Math.abs((long) username.hashCode())));
        director.setName("Persona");
        director.setLastname("Prueba");
        director.setEmail(username + "@example.com");
        director.setAddress(addressRepository.save(new Address()));
        director.setStatus(StatusType.ACTIVE);
        director = personRepository.save(director);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(director.getId().toString(), null, "ROLE_DIRECTOR"));

        Program newProgram = new Program();
        newProgram.setName("Carrera " + System.nanoTime());
        newProgram.setTotalYears(3);
        newProgram.setTotalCreditsRequired(0);
        program = programRepository.save(newProgram);

        Term newTerm = TermFixtures.newTerm();
        newTerm.setYear(2026);
        newTerm.setStatus(StatusType.ACTIVE);
        newTerm.setStartDate(YearMonth.of(2027, 3).atDay(1).atStartOfDay());
        newTerm.setEndDate(YearMonth.of(2027, 12).atEndOfMonth().atTime(23, 59));
        term = termRepository.save(newTerm);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void templateCreationFailingAfterPublishingTheFirstVersionLeavesNoTemplateNorVersions() {
        AtomicityTestSupport support = AtomicityTestSupport.observing(jdbcTemplate,
                "program_year_billing_template", "configuration_version");

        support.assertNothingPersistedWhenFailingAtStep(1, steps -> {
            failAfterRealActivation(steps);
            createTemplate.create(program.getId(), term.getId(), 10, YearBillingConfigurationRequest.defaults());
        });
    }

    @Test
    void templateCreationWithoutFailuresPersistsTheTemplateAndItsInitialVersions() {
        long versionsBefore = count("configuration_version");

        ProgramYearBillingTemplate template = createTemplate.create(program.getId(), term.getId(), 10, YearBillingConfigurationRequest.defaults());

        assertThat(count("program_year_billing_template where id = " + template.getId())).isEqualTo(1);
        assertThat(count("configuration_version")).isGreaterThan(versionsBefore);
    }

    @Test
    void configurationUpdateFailingAfterPublishingTheNewVersionLeavesTheVersionsUntouched() {
        ProgramYearBillingTemplate template = createTemplate.create(program.getId(), term.getId(), 10, YearBillingConfigurationRequest.defaults());
        AtomicityTestSupport support = AtomicityTestSupport.observing(jdbcTemplate,
                "program_year_billing_template", "configuration_version");

        support.assertNothingPersistedWhenFailingAtStep(1, steps -> {
            failAfterRealActivation(steps);
            updateConfiguration.update(template.getId(), configurationChangingTheLeadMonths());
        });
    }

    @Test
    void configurationUpdateWithoutFailuresPublishesANewVersion() {
        ProgramYearBillingTemplate template = createTemplate.create(program.getId(), term.getId(), 10, YearBillingConfigurationRequest.defaults());
        long versionsBefore = count("configuration_version");

        updateConfiguration.update(template.getId(), configurationChangingTheLeadMonths());

        assertThat(count("configuration_version")).isEqualTo(versionsBefore + 1);
    }

    private YearBillingConfigurationRequest configurationChangingTheLeadMonths() {
        return new YearBillingConfigurationRequest(null, null, null, 3, null, null, null, null, null, null, null, null, null);
    }

    private void failAfterRealActivation(AtomicityTestSupport.FailingStep steps) {
        Mockito.doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            steps.run(() -> { });
            return result;
        }).when(activator).supersedeOlderEffectiveVersions(any());
    }

    private long count(String tableAndFilter) {
        return jdbcTemplate.queryForObject("select count(*) from " + tableAndFilter, Long.class);
    }
}
