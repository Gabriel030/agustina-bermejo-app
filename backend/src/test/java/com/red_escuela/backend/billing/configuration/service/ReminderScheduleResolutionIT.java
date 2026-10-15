package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;
import com.red_escuela.backend.billing.configuration.domain.ProgramReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderNotice;
import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleSource;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleTarget;
import com.red_escuela.backend.billing.configuration.domain.ReminderSendingOverrides;
import com.red_escuela.backend.billing.configuration.domain.ResolvedReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.TeachingGroup;
import com.red_escuela.backend.billing.configuration.exception.InvalidCollectionReminderScheduleException;
import com.red_escuela.backend.billing.configuration.exception.ProposalAlreadyOpenException;
import com.red_escuela.backend.billing.configuration.usecase.ListConfigurationVersionHistoryUseCase;
import com.red_escuela.backend.billing.configuration.usecase.ProposeBillingConfigurationChangeUseCase;
import com.red_escuela.backend.billing.authorization.usecase.ApproveAuthorizationRequestUseCase;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersionStatus;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ReminderScheduleResolutionIT extends AbstractIntegrationTest {
    private static final LocalDate MARCH_THIRD = LocalDate.of(2027, 3, 3);
    private static final LocalDate MARCH_FOURTH = MARCH_THIRD.plusDays(1);

    @Autowired
    private ProposeBillingConfigurationChangeUseCase propose;
    @Autowired
    private ListConfigurationVersionHistoryUseCase history;
    @Autowired
    private BillingConfigurationReader reader;
    @Autowired
    private ApproveAuthorizationRequestUseCase approveRequest;
    @Autowired
    private ConfigurationVersionRepository versionRepository;
    @Autowired
    private ConfigurationVersionActivator activator;
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
    private Long nursingId;
    private Long radiologyId;
    private Long groupVersionId;

    @BeforeEach
    void setUp() {
        MutableClock.INSTANCE.setDate(MARCH_THIRD);
        director = transactionTemplate.execute(status -> {
            Person person = new Person();
            String username = "director." + System.nanoTime();
            person.setUsername(username);
            person.setDni(Long.toString(Math.abs((long) username.hashCode())));
            person.setName("Persona");
            person.setLastname("Prueba");
            person.setEmail(username + "@example.com");
            person.setAddress(addressRepository.save(new Address()));
            person.setStatus(StatusType.ACTIVE);
            person.getRoles().add(roleRepository.findByName("DIRECTOR").orElseThrow());
            return personRepository.save(person);
        });
        nursingId = insertProgram("Enfermería");
        radiologyId = insertProgram("Radiología");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(director.getId().toString(), null, "ROLE_DIRECTOR"));
    }

    @AfterEach
    void tearDown() {
        try {
            jdbcTemplate.execute("alter table configuration_version_status_change disable trigger trg_configuration_version_status_change_append_only");
            jdbcTemplate.update("update configuration_version set previous_version_id = null where proposed_by_id = ?", director.getId());
            jdbcTemplate.update("delete from configuration_version_status_change where configuration_version_id in (select id from configuration_version where proposed_by_id = ?)",
                    director.getId());
            jdbcTemplate.update("delete from configuration_version where proposed_by_id = ?", director.getId());
        } finally {
            jdbcTemplate.execute("alter table configuration_version_status_change enable trigger trg_configuration_version_status_change_append_only");
        }
        jdbcTemplate.update("delete from program where id in (?, ?)", nursingId, radiologyId);
        SecurityContextHolder.clearContext();
    }

    private Long insertProgram(String name) {
        return jdbcTemplate.queryForObject("insert into program (name, status, total_years, currency) values (?, true, 3, 'ARS') returning id", Long.class,
                name + "." + System.nanoTime());
    }

    private void publish(ConfigurationTopic topic, ConfigurationValues values, LocalDate effectiveFrom) {
        propose.proposeChange(topic, values, effectiveFrom, "motivo");
    }

    private ProgramReminderSchedule programSchedule(int noticeCount, ReminderSendingOverrides overrides) {
        return new ProgramReminderSchedule(new ReminderSchedule(false, ReminderSchedule.initial().notices().subList(0, noticeCount), overrides));
    }

    private ResolvedReminderSchedule resolveFor(Long programId, LocalDate date) {
        return reader.reminderScheduleFor(new ReminderScheduleTarget(TeachingGroup.PROGRAMS, programId), date);
    }

    private void publishGroupAndNursingException() {
        publish(ConfigurationTopic.ofReminderScheduleGroup(TeachingGroup.PROGRAMS), programSchedule(5, new ReminderSendingOverrides(null, null, null, null, null, null, 200, null, null)),
                MARCH_FOURTH);
        publish(ConfigurationTopic.ofReminderScheduleException(TeachingGroup.PROGRAMS, nursingId), programSchedule(4, ReminderSendingOverrides.none()), MARCH_FOURTH);
    }

    @Test
    @Tag("D-368")
    @Tag("D-369")
    void theExceptionOfAProgramWinsAndEveryOtherProgramGetsTheGroupWithItsOwnSettings() {
        publishGroupAndNursingException();

        assertThat(resolveFor(nursingId, MARCH_THIRD).source()).isEqualTo(ReminderScheduleSource.TEMPLATE);
        ResolvedReminderSchedule nursing = resolveFor(nursingId, MARCH_FOURTH);
        ResolvedReminderSchedule radiology = resolveFor(radiologyId, MARCH_FOURTH);

        assertThat(nursing.source()).isEqualTo(ReminderScheduleSource.EXCEPTION);
        assertThat(nursing.notices()).hasSize(4);
        assertThat(nursing.settings().dailyEmailCap()).isEqualTo(200);
        assertThat(radiology.source()).isEqualTo(ReminderScheduleSource.GROUP);
        assertThat(radiology.notices()).hasSize(5);
        assertThat(radiology.settings().dailyEmailCap()).isEqualTo(200);
        assertThat(reader.reminderScheduleFor(new ReminderScheduleTarget(TeachingGroup.MONTHLY_COURSES, 999L), MARCH_FOURTH).source())
                .isEqualTo(ReminderScheduleSource.TEMPLATE);
    }

    @Test
    @Tag("D-368")
    @Tag("D-350")
    void removingTheExceptionIsAVersionThatTakesEffectOnItsDateAndKeepsTheHistory() {
        publishGroupAndNursingException();
        MutableClock.INSTANCE.setDate(MARCH_THIRD.plusDays(5));
        ConfigurationTopic nursingTopic = ConfigurationTopic.ofReminderScheduleException(TeachingGroup.PROGRAMS, nursingId);

        publish(nursingTopic, new ProgramReminderSchedule(ReminderSchedule.followingGroup()), LocalDate.of(2027, 3, 10));

        assertThat(resolveFor(nursingId, LocalDate.of(2027, 3, 9)).source()).isEqualTo(ReminderScheduleSource.EXCEPTION);
        assertThat(resolveFor(nursingId, LocalDate.of(2027, 3, 10)).source()).isEqualTo(ReminderScheduleSource.GROUP);
        assertThat(resolveFor(nursingId, LocalDate.of(2027, 3, 10)).notices()).hasSize(5);
        assertThat(history.listVersionHistory(nursingTopic)).hasSize(2);
    }

    @Test
    @Tag("D-368")
    @Tag("D-116")
    void aTopicHasASingleOpenProposalButTheGroupAndTheExceptionAreIndependentTopics() {
        publishGroupAndNursingException();
        Person administrative = transactionTemplate.execute(status -> {
            Person person = new Person();
            String username = "administrative." + System.nanoTime();
            person.setUsername(username);
            person.setDni(Long.toString(Math.abs((long) username.hashCode())));
            person.setName("Persona");
            person.setLastname("Prueba");
            person.setEmail(username + "@example.com");
            person.setAddress(addressRepository.save(new Address()));
            person.setStatus(StatusType.ACTIVE);
            person.getRoles().add(roleRepository.findByName("ADMINISTRATIVO").orElseThrow());
            return personRepository.save(person);
        });
        try {
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(administrative.getId().toString(), null, "ROLE_ADMINISTRATIVO"));
            ConfigurationTopic nursingTopic = ConfigurationTopic.ofReminderScheduleException(TeachingGroup.PROGRAMS, nursingId);
            LocalDate march6 = MARCH_THIRD.plusDays(3);

            propose.proposeChange(nursingTopic, programSchedule(3, ReminderSendingOverrides.none()), march6, "motivo");

            assertThatThrownBy(() -> propose.proposeChange(nursingTopic, programSchedule(2, ReminderSendingOverrides.none()), march6.plusDays(1), "otro"))
                    .isInstanceOf(ProposalAlreadyOpenException.class);
            propose.proposeChange(ConfigurationTopic.ofReminderScheduleGroup(TeachingGroup.PROGRAMS), programSchedule(3, ReminderSendingOverrides.none()), march6, "motivo");
            assertThat(resolveFor(nursingId, MARCH_FOURTH).notices()).hasSize(4);
        } finally {
            try {
                jdbcTemplate.execute("alter table configuration_version_status_change disable trigger trg_configuration_version_status_change_append_only");
                jdbcTemplate.update("update configuration_version set previous_version_id = null where proposed_by_id = ?", administrative.getId());
                jdbcTemplate.update("delete from configuration_version_status_change where configuration_version_id in (select id from configuration_version where proposed_by_id = ?)",
                        administrative.getId());
                jdbcTemplate.update("delete from configuration_version where proposed_by_id = ?", administrative.getId());
                jdbcTemplate.update("delete from authorization_request_status_change where authorization_request_id in (select id from authorization_request where requested_by_id = ?)",
                        administrative.getId());
                jdbcTemplate.update("delete from authorization_request where requested_by_id = ?", administrative.getId());
            } finally {
                jdbcTemplate.execute("alter table configuration_version_status_change enable trigger trg_configuration_version_status_change_append_only");
            }
        }
    }

    @Test
    @Tag("D-368")
    void aGroupCannotFollowItselfAndAnInvalidListIsRejectedBeforeBeingSaved() {
        assertThatThrownBy(() -> publish(ConfigurationTopic.ofReminderScheduleGroup(TeachingGroup.PROGRAMS), new ProgramReminderSchedule(ReminderSchedule.followingGroup()),
                MARCH_FOURTH)).isInstanceOf(InvalidCollectionReminderScheduleException.class);
        assertThatThrownBy(() -> publish(ConfigurationTopic.ofReminderScheduleException(TeachingGroup.PROGRAMS, nursingId), programSchedule(5,
                new ReminderSendingOverrides(null, 0, null, null, null, null, null, null, null)), MARCH_FOURTH)).isInstanceOf(InvalidCollectionReminderScheduleException.class);
        assertThat(jdbcTemplate.queryForObject("select count(*) from configuration_version where proposed_by_id = ?", Long.class, director.getId())).isZero();
    }

    @Test
    @Tag("D-351")
    @Tag("D-350")
    @Tag("D-368")
    void aGroupProposalStaysPendingUntilTheDirectorApprovesItAndThenItRulesFromTheNextDay() {
        Person administrative = transactionTemplate.execute(status -> {
            Person person = new Person();
            String username = "administrative." + System.nanoTime();
            person.setUsername(username);
            person.setDni(Long.toString(Math.abs((long) username.hashCode())));
            person.setName("Persona");
            person.setLastname("Prueba");
            person.setEmail(username + "@example.com");
            person.setAddress(addressRepository.save(new Address()));
            person.setStatus(StatusType.ACTIVE);
            person.getRoles().add(roleRepository.findByName("ADMINISTRATIVO").orElseThrow());
            return personRepository.save(person);
        });
        try {
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(administrative.getId().toString(), null, "ROLE_ADMINISTRATIVO"));
            ReminderSchedule changed = ReminderSchedule.initial();
            List<ReminderNotice> notices = changed.notices().stream()
                    .map(notice -> notice.noticeKey().equals("before-due") ? new ReminderNotice(notice.noticeKey(), notice.messageType(), notice.milestone(), -2, notice.channels(), null) : notice)
                    .toList();
            ConfigurationTopic groupTopic = ConfigurationTopic.ofReminderScheduleGroup(TeachingGroup.PROGRAMS);

            Long requestId = ((BillingOperationOutcome.AwaitingAuthorization<ConfigurationVersion>) propose.proposeChange(groupTopic,
                    new ProgramReminderSchedule(new ReminderSchedule(false, notices, null)), MARCH_FOURTH, "motivo")).authorizationRequestId();

            assertThat(versionRepository.findFirstByAuthorizationRequestId(requestId).orElseThrow().getStatus()).isEqualTo(ConfigurationVersionStatus.PROPOSED);
            assertThat(beforeDueDaysOn(MARCH_FOURTH)).isEqualTo(-1);
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(director.getId().toString(), null, "ROLE_DIRECTOR"));
            approveRequest.approve(requestId, "de acuerdo");
            assertThat(versionRepository.findFirstByAuthorizationRequestId(requestId).orElseThrow().getStatus()).isEqualTo(ConfigurationVersionStatus.SCHEDULED);
            assertThat(beforeDueDaysOn(MARCH_THIRD)).isEqualTo(-1);
            MutableClock.INSTANCE.setDate(MARCH_FOURTH);
            activator.activateDueVersions();
            assertThat(beforeDueDaysOn(MARCH_FOURTH)).isEqualTo(-2);
        } finally {
            try {
                jdbcTemplate.execute("alter table configuration_version_status_change disable trigger trg_configuration_version_status_change_append_only");
                jdbcTemplate.update("update configuration_version set previous_version_id = null where proposed_by_id = ?", administrative.getId());
                jdbcTemplate.update("delete from configuration_version_status_change where configuration_version_id in (select id from configuration_version where proposed_by_id = ?)",
                        administrative.getId());
                jdbcTemplate.update("delete from configuration_version where proposed_by_id = ?", administrative.getId());
                jdbcTemplate.update("delete from authorization_request_status_change where authorization_request_id in (select id from authorization_request where requested_by_id = ?)",
                        administrative.getId());
                jdbcTemplate.update("delete from authorization_request where requested_by_id = ?", administrative.getId());
            } finally {
                jdbcTemplate.execute("alter table configuration_version_status_change enable trigger trg_configuration_version_status_change_append_only");
            }
        }
    }

    private int beforeDueDaysOn(LocalDate date) {
        return reader.reminderScheduleFor(new ReminderScheduleTarget(TeachingGroup.PROGRAMS, null), date).notices().stream()
                .filter(notice -> notice.noticeKey().equals("before-due")).findFirst().orElseThrow().offsetDays();
    }
}
