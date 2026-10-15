package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestAccessDeniedException;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestStatus;
import com.red_escuela.backend.billing.authorization.repository.AuthorizationRequestRepository;
import com.red_escuela.backend.billing.authorization.usecase.ApproveAuthorizationRequestUseCase;
import com.red_escuela.backend.billing.configuration.domain.AllocationSettings;
import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersionStatus;
import com.red_escuela.backend.billing.configuration.exception.EffectiveFromBeforeNextDayException;
import com.red_escuela.backend.billing.configuration.exception.InvalidCollectionReminderSettingsException;
import com.red_escuela.backend.billing.configuration.exception.ProposalAlreadyOpenException;
import com.red_escuela.backend.billing.configuration.exception.ProposalExpiredException;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.configuration.usecase.ListConfigurationVersionHistoryUseCase;
import com.red_escuela.backend.billing.configuration.usecase.ProposeBillingConfigurationChangeUseCase;
import com.red_escuela.backend.billing.configuration.usecase.ValidateBillingConfigurationChangeUseCase;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
class CollectionReminderSettingsVersioningIT extends AbstractIntegrationTest {
    private static final ConfigurationTopic TOPIC = ConfigurationTopic.ofGlobal(ConfigurationScope.COLLECTION_REMINDERS);
    private static final LocalDate MARCH_THIRD = LocalDate.of(2027, 3, 3);
    private static final LocalDate MARCH_FOURTH = MARCH_THIRD.plusDays(1);

    @Autowired
    private ProposeBillingConfigurationChangeUseCase propose;
    @Autowired
    private ValidateBillingConfigurationChangeUseCase validate;
    @Autowired
    private ListConfigurationVersionHistoryUseCase history;
    @Autowired
    private ApproveAuthorizationRequestUseCase approveRequest;
    @Autowired
    private AuthorizationRequestRepository requestRepository;
    @Autowired
    private ConfigurationVersionRepository versionRepository;
    @Autowired
    private ConfigurationVersionActivator activator;
    @Autowired
    private BillingConfigurationReader reader;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private Person director;
    private Person administrative;
    private Person teacher;

    @BeforeEach
    void setUp() {
        MutableClock.INSTANCE.setDate(MARCH_THIRD);
        director = persistPerson("director", "DIRECTOR");
        administrative = persistPerson("administrative", "ADMINISTRATIVO");
        teacher = persistPerson("teacher", "DOCENTE");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("alter table configuration_version_status_change disable trigger trg_configuration_version_status_change_append_only");
        String people = List.of(director, administrative, teacher).stream().map(person -> person.getId().toString()).collect(java.util.stream.Collectors.joining(","));
        jdbcTemplate.update("update configuration_version set previous_version_id = null where proposed_by_id in (" + people + ")");
        for (Person person : List.of(director, administrative, teacher)) {
            jdbcTemplate.update("delete from configuration_version_status_change where configuration_version_id in (select id from configuration_version where proposed_by_id = ?)", person.getId());
            jdbcTemplate.update("delete from configuration_version where proposed_by_id = ?", person.getId());
            jdbcTemplate.update("delete from authorization_request_status_change where authorization_request_id in (select id from authorization_request where requested_by_id = ?)", person.getId());
            jdbcTemplate.update("delete from authorization_request where requested_by_id = ?", person.getId());
        }
        jdbcTemplate.execute("alter table configuration_version_status_change enable trigger trg_configuration_version_status_change_append_only");
        SecurityContextHolder.clearContext();
    }

    private Person persistPerson(String prefix, String roleName) {
        return transactionTemplate.execute(status -> {
            Person person = new Person();
            String username = prefix + "." + System.nanoTime();
            person.setUsername(username);
            person.setDni(Long.toString(Math.abs((long) (username).hashCode())));
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

    private static CollectionReminderSettings withDailyCap(int cap) {
        CollectionReminderSettings initial = CollectionReminderSettings.initial();
        return new CollectionReminderSettings(initial.suspensionNoticeEnabled(), initial.postWithdrawalSummaryMonths(),
                initial.minDaysBetweenEmails(), initial.maxReminderEmailsPerMonth(), initial.emailWindowStart(), initial.emailWindowEnd(), cap,
                initial.contactListDaysOverdue(), initial.contactListOverdueInstallments());
    }

    private static CollectionReminderSettings withMinDaysBetweenEmails(int days) {
        CollectionReminderSettings initial = CollectionReminderSettings.initial();
        return new CollectionReminderSettings(initial.suspensionNoticeEnabled(), initial.postWithdrawalSummaryMonths(), days,
                initial.maxReminderEmailsPerMonth(), initial.emailWindowStart(), initial.emailWindowEnd(), initial.dailyEmailCap(),
                initial.contactListDaysOverdue(), initial.contactListOverdueInstallments());
    }

    private Long proposeAwaiting(CollectionReminderSettings values, LocalDate effectiveFrom) {
        return ((BillingOperationOutcome.AwaitingAuthorization<ConfigurationVersion>) propose.proposeChange(TOPIC, values, effectiveFrom, "motivo")).authorizationRequestId();
    }

    private ConfigurationVersion publish(CollectionReminderSettings values, LocalDate effectiveFrom) {
        return ((BillingOperationOutcome.Processed<ConfigurationVersion>) propose.proposeChange(TOPIC, values, effectiveFrom, "motivo")).result();
    }

    private ConfigurationVersion versionOf(Long requestId) {
        return versionRepository.findFirstByAuthorizationRequestId(requestId).orElseThrow();
    }

    private int capOn(LocalDate date) {
        return reader.collectionReminderSettingsEffectiveOn(date).dailyEmailCap();
    }

    private long count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    @Test
    @Tag("D-351")
    @Tag("D-114")
    @Tag("D-117")
    void administrativeProposalLeavesTheCurrentCapRulingAndNotifiesTheDirector() {
        authenticateAs(administrative);

        Long requestId = proposeAwaiting(withDailyCap(280), MARCH_FOURTH);

        assertThat(versionOf(requestId).getStatus()).isEqualTo(ConfigurationVersionStatus.PROPOSED);
        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus()).isEqualTo(AuthorizationRequestStatus.PENDING);
        assertThat(capOn(MARCH_THIRD)).isEqualTo(250);
        assertThat(capOn(MARCH_FOURTH)).isEqualTo(250);
        await().until(() -> count("select count(*) from notification where recipient_id = ? and type = 'AUTHORIZATION_REQUESTED'", director.getId()) >= 1);
    }

    @Test
    @Tag("D-351")
    @Tag("D-095")
    void directorApprovalSchedulesTheVersionAndItRulesFromTheNextDay() {
        authenticateAs(administrative);
        Long requestId = proposeAwaiting(withDailyCap(280), MARCH_FOURTH);
        authenticateAs(director);

        approveRequest.approve(requestId, "de acuerdo");

        assertThat(versionOf(requestId).getStatus()).isEqualTo(ConfigurationVersionStatus.SCHEDULED);
        assertThat(capOn(MARCH_THIRD)).isEqualTo(250);
        MutableClock.INSTANCE.setDate(MARCH_FOURTH);
        activator.activateDueVersions();
        assertThat(versionOf(requestId).getStatus()).isEqualTo(ConfigurationVersionStatus.EFFECTIVE);
        assertThat(capOn(MARCH_FOURTH)).isEqualTo(280);
        assertThat(history.listVersionHistory(TOPIC)).extracting(ConfigurationVersion::getStatus).first().isEqualTo(ConfigurationVersionStatus.EFFECTIVE);
    }

    @Test
    @Tag("D-351")
    @Tag("D-095")
    void directorPublishesDirectlyForTomorrowAndTheReaderResolvesByDate() {
        authenticateAs(director);

        ConfigurationVersion published = publish(withDailyCap(280), MARCH_FOURTH);

        assertThat(published.getStatus()).isEqualTo(ConfigurationVersionStatus.SCHEDULED);
        assertThat(published.isDirectPublication()).isTrue();
        assertThat(capOn(MARCH_THIRD)).isEqualTo(250);
        assertThat(capOn(MARCH_FOURTH)).isEqualTo(280);
        assertThat(reader.findCollectionReminderSettingsEffectiveOn(MARCH_FOURTH)).contains(withDailyCap(280));
    }

    @Test
    @Tag("D-350")
    void sameDayVigencyIsRejectedWhenProposingAndWhenPublishing() {
        authenticateAs(administrative);
        assertThatThrownBy(() -> proposeAwaiting(withDailyCap(280), MARCH_THIRD)).isInstanceOf(EffectiveFromBeforeNextDayException.class);
        assertThat(validate.validateChange(TOPIC, withDailyCap(280), MARCH_THIRD, "m")).extracting("code").containsExactly("EFFECTIVE_FROM_BEFORE_NEXT_DAY");
        assertThat(validate.validateChange(TOPIC, withDailyCap(280), MARCH_FOURTH, "m")).isEmpty();

        authenticateAs(director);
        assertThatThrownBy(() -> publish(withDailyCap(280), MARCH_THIRD)).isInstanceOf(EffectiveFromBeforeNextDayException.class);
        assertThat(count("select count(*) from configuration_version where proposed_by_id in (?, ?)", administrative.getId(), director.getId())).isZero();
    }

    @Test
    @Tag("D-350")
    @Tag("D-180")
    void approvingOnTheVigencyDayIsAConflictAndTheProposalIsClosedWithoutTakingEffect() {
        authenticateAs(administrative);
        Long requestId = proposeAwaiting(withDailyCap(280), MARCH_FOURTH);
        MutableClock.INSTANCE.setDate(MARCH_FOURTH);
        authenticateAs(director);

        assertThatThrownBy(() -> approveRequest.approve(requestId, "tarde")).isInstanceOf(ProposalExpiredException.class);

        assertThat(versionOf(requestId).getStatus()).isEqualTo(ConfigurationVersionStatus.REJECTED);
        assertThat(capOn(MARCH_FOURTH)).isEqualTo(250);
    }

    @Test
    @Tag("D-350")
    void aProposalAcceptedWithTwoDaysAndApprovedTheNextDayStillTakesEffectOnItsOwnDate() {
        LocalDate march5 = MARCH_THIRD.plusDays(2);
        authenticateAs(administrative);
        Long requestId = proposeAwaiting(withMinDaysBetweenEmails(2), march5);
        MutableClock.INSTANCE.setDate(MARCH_FOURTH);
        authenticateAs(director);

        approveRequest.approve(requestId, "ok");

        assertThat(versionOf(requestId).getStatus()).isEqualTo(ConfigurationVersionStatus.SCHEDULED);
        assertThat(reader.collectionReminderSettingsEffectiveOn(MARCH_FOURTH).minDaysBetweenEmails()).isEqualTo(3);
        assertThat(reader.collectionReminderSettingsEffectiveOn(march5).minDaysBetweenEmails()).isEqualTo(2);
    }

    @Test
    @Tag("D-351")
    void incoherentValuesAreRejectedWithTheViolationOfTheField() {
        authenticateAs(administrative);

        assertThatThrownBy(() -> proposeAwaiting(withMinDaysBetweenEmails(0), MARCH_FOURTH)).isInstanceOf(InvalidCollectionReminderSettingsException.class);
        assertThat(validate.validateChange(TOPIC, withMinDaysBetweenEmails(-1), MARCH_FOURTH, "m")).extracting("code").containsExactly("INVALID_COLLECTION_REMINDER_SETTINGS");
        assertThat(validate.validateChange(TOPIC, new CollectionReminderSettings(true, 6, 3, 4, LocalTime.of(11, 0), LocalTime.of(9, 0), 250, 14, 2),
                MARCH_FOURTH, "m")).hasSize(1);
        assertThat(count("select count(*) from configuration_version where proposed_by_id = ?", administrative.getId())).isZero();
    }

    @Test
    @Tag("D-116")
    void onlyOneOpenProposalPerTopicAndItCoexistsWithAnotherTopicProposal() {
        authenticateAs(administrative);
        proposeAwaiting(withDailyCap(280), MARCH_FOURTH);

        assertThatThrownBy(() -> proposeAwaiting(withDailyCap(300), MARCH_FOURTH.plusDays(1))).isInstanceOf(ProposalAlreadyOpenException.class);
        propose.proposeChange(ConfigurationTopic.ofGlobal(ConfigurationScope.ALLOCATION), new AllocationSettings(false), MARCH_FOURTH, "m");

        assertThat(count("select count(*) from configuration_version where status = 'PROPOSED' and scope = 'COLLECTION_REMINDERS' and proposed_by_id = ?", administrative.getId())).isEqualTo(1);
        assertThat(count("select count(*) from configuration_version where status = 'PROPOSED' and proposed_by_id = ?", administrative.getId())).isEqualTo(2);
    }

    @Test
    @Tag("D-110")
    void teacherCannotProposeOrConsultAndAdministrativeCannotApprove() {
        authenticateAs(teacher);
        assertThatThrownBy(() -> proposeAwaiting(withDailyCap(280), MARCH_FOURTH)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> history.listVersionHistory(TOPIC)).isInstanceOf(AccessDeniedException.class);

        authenticateAs(administrative);
        Long requestId = proposeAwaiting(withDailyCap(280), MARCH_FOURTH);
        assertThatThrownBy(() -> approveRequest.approve(requestId, "me apruebo")).isInstanceOf(AuthorizationRequestAccessDeniedException.class);
        assertThat(versionOf(requestId).getStatus()).isEqualTo(ConfigurationVersionStatus.PROPOSED);
    }
}
