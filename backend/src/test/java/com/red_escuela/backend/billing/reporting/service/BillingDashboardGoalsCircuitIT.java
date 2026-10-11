package com.red_escuela.backend.billing.reporting.service;

import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestStatus;
import com.red_escuela.backend.billing.authorization.repository.AuthorizationRequestRepository;
import com.red_escuela.backend.billing.authorization.usecase.ApproveAuthorizationRequestUseCase;
import com.red_escuela.backend.billing.configuration.domain.BillingDashboardGoals;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersionStatus;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.configuration.service.ConfigurationVersionActivator;
import com.red_escuela.backend.billing.configuration.usecase.ProposeBillingConfigurationChangeUseCase;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Tag("D-357")
@Tag("D-114")
class BillingDashboardGoalsCircuitIT extends AbstractIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2031, 6, 1);
    private static final LocalDate NEXT_MONTH = LocalDate.of(2031, 7, 1);
    private static final ConfigurationTopic GOALS = ConfigurationTopic.ofGlobal(ConfigurationScope.BILLING_DASHBOARD_GOALS);

    @Autowired
    private ProposeBillingConfigurationChangeUseCase propose;
    @Autowired
    private ApproveAuthorizationRequestUseCase approveRequest;
    @Autowired
    private ConfigurationVersionRepository versionRepository;
    @Autowired
    private AuthorizationRequestRepository requestRepository;
    @Autowired
    private BillingConfigurationReader reader;
    @Autowired
    private ConfigurationVersionActivator activator;
    @Autowired
    private DashboardGoalsProvider goalsProvider;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private RoleRepository roleRepository;

    private Person director;
    private Person administrative;

    @BeforeEach
    void setUp() {
        MutableClock.INSTANCE.setDate(TODAY);
        director = persistPerson("goals.director", "DIRECTOR");
        administrative = persistPerson("goals.administrative", "ADMINISTRATIVO");
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("alter table configuration_version_status_change disable trigger trg_configuration_version_status_change_append_only");
        String people = director.getId() + "," + administrative.getId();
        jdbc.update("update configuration_version set previous_version_id = null where proposed_by_id in (" + people + ")");
        for (Person person : List.of(director, administrative)) {
            jdbc.update("delete from configuration_version_status_change where configuration_version_id in (select id from configuration_version where proposed_by_id = ?)", person.getId());
            jdbc.update("delete from configuration_version where proposed_by_id = ?", person.getId());
            jdbc.update("delete from authorization_request_status_change where authorization_request_id in (select id from authorization_request where requested_by_id = ?)", person.getId());
            jdbc.update("delete from authorization_request where requested_by_id = ?", person.getId());
        }
        jdbc.execute("alter table configuration_version_status_change enable trigger trg_configuration_version_status_change_append_only");
        SecurityContextHolder.clearContext();
    }

    private Person persistPerson(String prefix, String roleName) {
        return transactionTemplate.execute(status -> {
            Person person = new Person();
            String username = prefix + "." + System.nanoTime();
            person.setUsername(username);
            person.setDni(Long.toString(Math.abs((long) username.hashCode())));
            person.setName("Persona");
            person.setLastname("Prueba");
            person.setEmail(username + "@example.com");
            person.setAddress(addressRepository.save(new Address()));
            person.setStatus(StatusType.ACTIVE);
            person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
            return personRepository.save(person);
        });
    }

    private void authenticateAs(Person person) {
        String authority = "ROLE_" + person.getRoles().iterator().next().getName();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(person.getId().toString(), null, authority));
    }

    private static BillingDashboardGoals onTimeGoal(String points) {
        return new BillingDashboardGoals(Percentage.ofPoints(new BigDecimal(points)), null, 3, null);
    }

    @Test
    void withoutAnyVersionTheInitialGoalOfEightyFivePercentRules() {
        assertThat(reader.findBillingDashboardGoalsEffectiveOn(TODAY)).isEmpty();
        assertThat(goalsProvider.findGoalInEffect(com.red_escuela.backend.billing.reporting.domain.GoalIndicator.ON_TIME_PAYMENT).orElseThrow()
                .threshold().magnitude()).isEqualByComparingTo("0.85");
        assertThat(goalsProvider.findGoalInEffect(com.red_escuela.backend.billing.reporting.domain.GoalIndicator.OVERDUE_RATIO)).isEmpty();
    }

    @Test
    void administrativeProposesANewGoalAndTheDirectorApprovesItBeforeItStartsToRule() {
        authenticateAs(director);
        propose.proposeChange(GOALS, onTimeGoal("85"), TODAY, "metas iniciales");
        authenticateAs(administrative);

        BillingOperationOutcome<ConfigurationVersion> outcome = propose.proposeChange(GOALS, onTimeGoal("90"), NEXT_MONTH, "subir la exigencia");

        Long requestId = ((BillingOperationOutcome.AwaitingAuthorization<ConfigurationVersion>) outcome).authorizationRequestId();
        ConfigurationVersion proposed = versionRepository.findFirstByAuthorizationRequestId(requestId).orElseThrow();
        assertThat(proposed.getStatus()).isEqualTo(ConfigurationVersionStatus.PROPOSED);
        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus()).isEqualTo(AuthorizationRequestStatus.PENDING);
        assertThat(reader.billingDashboardGoalsEffectiveOn(NEXT_MONTH).onTimePaymentAtLeast().points()).isEqualByComparingTo("85");

        authenticateAs(director);
        approveRequest.approve(requestId, "de acuerdo");
        assertThat(versionRepository.findById(proposed.getId()).orElseThrow().getStatus()).isEqualTo(ConfigurationVersionStatus.SCHEDULED);
        assertThat(reader.billingDashboardGoalsEffectiveOn(TODAY).onTimePaymentAtLeast().points()).isEqualByComparingTo("85");

        MutableClock.INSTANCE.setDate(NEXT_MONTH);
        activator.activateDueVersions();
        assertThat(reader.billingDashboardGoalsEffectiveOn(NEXT_MONTH).onTimePaymentAtLeast().points()).isEqualByComparingTo("90");
        assertThat(goalsProvider.findGoalInEffect(com.red_escuela.backend.billing.reporting.domain.GoalIndicator.RECEIPT_REVIEW_TIME).orElseThrow()
                .threshold().magnitude()).isEqualByComparingTo("3");
    }
}
