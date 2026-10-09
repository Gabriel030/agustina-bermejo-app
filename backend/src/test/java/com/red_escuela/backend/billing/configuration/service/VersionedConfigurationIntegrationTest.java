package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestAccessDeniedException;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestStatus;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationResumeOutcome;
import com.red_escuela.backend.billing.authorization.repository.AuthorizationRequestRepository;
import com.red_escuela.backend.billing.authorization.service.AuthorizationRequestExpirer;
import com.red_escuela.backend.billing.authorization.usecase.ApproveAuthorizationRequestUseCase;
import com.red_escuela.backend.billing.authorization.usecase.RejectAuthorizationRequestUseCase;
import com.red_escuela.backend.billing.authorization.usecase.WithdrawAuthorizationRequestUseCase;
import com.red_escuela.backend.billing.configuration.domain.AllocationSettings;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersionStatus;
import com.red_escuela.backend.billing.configuration.domain.IrregularTermTypes;
import com.red_escuela.backend.billing.configuration.exception.CancellationReasonRequiredException;
import com.red_escuela.backend.billing.configuration.exception.ChangeReasonRequiredException;
import com.red_escuela.backend.billing.configuration.exception.ProposalAlreadyOpenException;
import com.red_escuela.backend.billing.configuration.exception.ProposalExpiredException;
import com.red_escuela.backend.billing.configuration.exception.RetroactiveEffectiveDateException;
import com.red_escuela.backend.billing.configuration.exception.VersionAlreadyEffectiveException;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.configuration.usecase.CancelScheduledConfigurationVersionUseCase;
import com.red_escuela.backend.billing.configuration.usecase.ListConfigurationVersionHistoryUseCase;
import com.red_escuela.backend.billing.configuration.usecase.ProposeBillingConfigurationChangeUseCase;
import com.red_escuela.backend.billing.configuration.usecase.ValidateBillingConfigurationChangeUseCase;
import com.red_escuela.backend.billing.dto.CreateLatePaymentSettingsRequest;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.billing.usecase.latepaymentsettings.CreateLatePaymentSettingsUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.domain.Address;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
class VersionedConfigurationIntegrationTest extends AbstractIntegrationTest {
    private static final ConfigurationTopic ALLOCATION = ConfigurationTopic.ofGlobal(ConfigurationScope.ALLOCATION);
    private static final ConfigurationTopic IRREGULAR = ConfigurationTopic.ofGlobal(ConfigurationScope.IRREGULAR_TERMS);
    private static final LocalDate TODAY = LocalDate.of(2027, 11, 15);
    private static final LocalDate DECEMBER_FIRST = LocalDate.of(2027, 12, 1);

    @Autowired
    private ProposeBillingConfigurationChangeUseCase propose;
    @Autowired
    private ValidateBillingConfigurationChangeUseCase validate;
    @Autowired
    private CancelScheduledConfigurationVersionUseCase cancel;
    @Autowired
    private ListConfigurationVersionHistoryUseCase history;
    @Autowired
    private ApproveAuthorizationRequestUseCase approveRequest;
    @Autowired
    private RejectAuthorizationRequestUseCase rejectRequest;
    @Autowired
    private WithdrawAuthorizationRequestUseCase withdrawRequest;
    @Autowired
    private AuthorizationRequestRepository requestRepository;
    @Autowired
    private AuthorizationRequestExpirer expirer;
    @Autowired
    private ConfigurationVersionRepository versionRepository;
    @Autowired
    private ConfigurationVersionActivator activator;
    @Autowired
    private BillingConfigurationReader reader;
    @Autowired
    private CreateLatePaymentSettingsUseCase createLatePayment;
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
        MutableClock.INSTANCE.setDate(TODAY);
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

    private Long propose(ConfigurationTopic topic, boolean autoApply, LocalDate start) {
        return ((BillingOperationOutcome.AwaitingAuthorization<ConfigurationVersion>) propose.proposeChange(topic, valuesFor(topic, autoApply), start, "motivo")).authorizationRequestId();
    }

    private static com.red_escuela.backend.billing.configuration.domain.ConfigurationValues valuesFor(ConfigurationTopic topic, boolean flag) {
        return topic.scope() == ConfigurationScope.ALLOCATION ? new AllocationSettings(flag) : new IrregularTermTypes(flag ? List.of("Intensivo") : List.of());
    }

    private ConfigurationVersion publish(ConfigurationTopic topic, boolean flag, LocalDate start) {
        return ((BillingOperationOutcome.Processed<ConfigurationVersion>) propose.proposeChange(topic, valuesFor(topic, flag), start, "motivo")).result();
    }

    private ConfigurationVersion versionOf(Long requestId) {
        return versionRepository.findFirstByAuthorizationRequestId(requestId).orElseThrow();
    }

    private boolean autoApplyOn(LocalDate date) {
        return reader.findAllocationSettingsEffectiveOn(date).orElseThrow().autoApplyCreditBalance();
    }

    private long count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    @Test
    @Tag("D-114")
    @Tag("D-095")
    @Tag("D-117")
    void administrativeProposesDirectorApprovesAndTheVersionRulesFromItsStartDate() {
        authenticateAs(director);
        publish(ALLOCATION, true, TODAY);
        authenticateAs(administrative);

        Long requestId = propose(ALLOCATION, false, DECEMBER_FIRST);

        ConfigurationVersion proposed = versionOf(requestId);
        assertThat(proposed.getStatus()).isEqualTo(ConfigurationVersionStatus.PROPOSED);
        assertThat(proposed.getPreviousVersionId()).isNotNull();
        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus()).isEqualTo(AuthorizationRequestStatus.PENDING);
        assertThat(autoApplyOn(DECEMBER_FIRST)).isTrue();
        await().until(() -> count("select count(*) from notification where recipient_id = ? and type = 'AUTHORIZATION_REQUESTED'", director.getId()) >= 1);

        authenticateAs(director);
        approveRequest.approve(requestId, "de acuerdo");

        ConfigurationVersion scheduled = versionRepository.findById(proposed.getId()).orElseThrow();
        assertThat(scheduled.getStatus()).isEqualTo(ConfigurationVersionStatus.SCHEDULED);
        assertThat(scheduled.getDecidedById()).isEqualTo(director.getId());
        assertThat(scheduled.isDirectPublication()).isFalse();
        assertThat(autoApplyOn(TODAY)).isTrue();
        assertThat(autoApplyOn(DECEMBER_FIRST.minusDays(1))).isTrue();
        assertThat(autoApplyOn(DECEMBER_FIRST)).isFalse();
        await().until(() -> count("select count(*) from notification where recipient_id = ? and type = 'CONFIGURATION_CHANGE_APPROVED'", administrative.getId()) == 1);

        MutableClock.INSTANCE.setDate(DECEMBER_FIRST);
        activator.activateDueVersions();
        assertThat(versionRepository.findById(proposed.getId()).orElseThrow().getStatus()).isEqualTo(ConfigurationVersionStatus.EFFECTIVE);
        assertThat(count("select count(*) from configuration_version where topic_key = 'ALLOCATION' and status = 'SUPERSEDED' and proposed_by_id = ?", director.getId())).isEqualTo(1);
        assertThat(autoApplyOn(DECEMBER_FIRST)).isFalse();
        assertThat(history.listVersionHistory(ALLOCATION).stream().map(ConfigurationVersion::getStatus).toList().subList(0, 2))
                .containsExactly(ConfigurationVersionStatus.EFFECTIVE, ConfigurationVersionStatus.SUPERSEDED);
    }

    @Test
    @Tag("D-223")
    @Tag("D-095")
    void directorPublishesWithoutAnAuthorizationRequest() {
        authenticateAs(director);

        ConfigurationVersion future = publish(ALLOCATION, false, DECEMBER_FIRST);
        ConfigurationVersion today = publish(IRREGULAR, true, TODAY);

        assertThat(future.getStatus()).isEqualTo(ConfigurationVersionStatus.SCHEDULED);
        assertThat(future.isDirectPublication()).isTrue();
        assertThat(future.getAuthorizationRequestId()).isNull();
        assertThat(today.getStatus()).isEqualTo(ConfigurationVersionStatus.EFFECTIVE);
        assertThat(count("select count(*) from authorization_request where requested_by_id = ?", director.getId())).isZero();
        assertThat(reader.findIrregularTermTypesEffectiveOn(TODAY).orElseThrow().types()).containsExactly("Intensivo");
    }

    @Test
    @Tag("D-116")
    void onlyOneOpenProposalPerTopicWhileDifferentTopicsCoexist() {
        authenticateAs(administrative);
        propose(ALLOCATION, false, DECEMBER_FIRST);

        assertThatThrownBy(() -> propose(ALLOCATION, true, DECEMBER_FIRST.plusDays(1))).isInstanceOf(ProposalAlreadyOpenException.class);
        propose(IRREGULAR, true, DECEMBER_FIRST);

        assertThat(count("select count(*) from configuration_version where status = 'PROPOSED' and proposed_by_id = ?", administrative.getId())).isEqualTo(2);
        assertThat(count("select count(*) from authorization_request where requested_by_id = ?", administrative.getId())).isEqualTo(2);
    }

    @Test
    @Tag("D-116")
    void twoThreadsProposingTheSameTopicLeaveExactlyOneOpenProposalAndNeverFail500() throws Exception {
        authenticateAs(administrative);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<String> attempt = () -> {
            authenticateAs(administrative);
            start.await();
            try {
                propose(ALLOCATION, false, DECEMBER_FIRST);
                return "OPENED";
            } catch (ProposalAlreadyOpenException duplicate) {
                return "DUPLICATE";
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
        Future<String> first = executor.submit(attempt);
        Future<String> second = executor.submit(attempt);
        start.countDown();
        List<String> outcomes = List.of(first.get(), second.get());
        executor.shutdown();

        assertThat(outcomes).containsExactlyInAnyOrder("OPENED", "DUPLICATE");
        assertThat(count("select count(*) from configuration_version where status = 'PROPOSED' and proposed_by_id = ?", administrative.getId())).isEqualTo(1);
        assertThat(count("select count(*) from authorization_request where requested_by_id = ?", administrative.getId())).isEqualTo(1);
    }

    @Test
    @Tag("D-110")
    void rolesAreEnforced() {
        authenticateAs(teacher);
        assertThatThrownBy(() -> propose(ALLOCATION, false, DECEMBER_FIRST)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> history.listVersionHistory(ALLOCATION)).isInstanceOf(AccessDeniedException.class);

        authenticateAs(administrative);
        Long requestId = propose(ALLOCATION, false, DECEMBER_FIRST);
        assertThatThrownBy(() -> approveRequest.approve(requestId, "me apruebo")).isInstanceOf(AuthorizationRequestAccessDeniedException.class);
        assertThatThrownBy(() -> cancel.cancelScheduledVersion(versionOf(requestId).getId(), "x")).isInstanceOf(AccessDeniedException.class);
        assertThat(versionOf(requestId).getStatus()).isEqualTo(ConfigurationVersionStatus.PROPOSED);
    }

    @Test
    @Tag("D-115")
    void directorCancelsAnApprovedChangeBeforeItStartsButNotOnceItStarted() {
        authenticateAs(administrative);
        Long requestId = propose(ALLOCATION, false, DECEMBER_FIRST);
        authenticateAs(director);
        publish(IRREGULAR, true, DECEMBER_FIRST);
        approveRequest.approve(requestId, "ok");
        Long versionId = versionOf(requestId).getId();

        assertThatThrownBy(() -> cancel.cancelScheduledVersion(versionId, " ")).isInstanceOf(CancellationReasonRequiredException.class);
        cancel.cancelScheduledVersion(versionId, "error de carga");

        ConfigurationVersion cancelled = versionRepository.findById(versionId).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(ConfigurationVersionStatus.CANCELLED);
        assertThat(cancelled.getCancellationReason()).isEqualTo("error de carga");
        assertThat(cancelled.getCancelledById()).isEqualTo(director.getId());
        await().until(() -> count("select count(*) from notification where recipient_id = ? and type = 'CONFIGURATION_VERSION_CANCELLED'", administrative.getId()) == 1);

        ConfigurationVersion scheduled = publish(ALLOCATION, true, DECEMBER_FIRST);
        MutableClock.INSTANCE.setDate(DECEMBER_FIRST);
        assertThatThrownBy(() -> cancel.cancelScheduledVersion(scheduled.getId(), "tarde")).isInstanceOf(VersionAlreadyEffectiveException.class);
        assertThat(versionRepository.findById(scheduled.getId()).orElseThrow().getStatus()).isEqualTo(ConfigurationVersionStatus.SCHEDULED);
    }

    @Test
    @Tag("D-111")
    void startDateInThePastIsRejectedAndTodayOrTomorrowAreAccepted() {
        authenticateAs(administrative);
        assertThatThrownBy(() -> propose(ALLOCATION, false, TODAY.minusDays(1))).isInstanceOf(RetroactiveEffectiveDateException.class);
        propose(ALLOCATION, false, TODAY);
        propose(IRREGULAR, false, TODAY.plusDays(1));
        assertThatThrownBy(() -> propose.proposeChange(ALLOCATION, new AllocationSettings(true), DECEMBER_FIRST, " ")).isInstanceOf(ChangeReasonRequiredException.class);

        assertThat(validate.validateChange(ALLOCATION, new AllocationSettings(true), TODAY.minusDays(1), "m")).extracting("code").containsExactly("RETROACTIVE_EFFECTIVE_DATE");
        assertThat(validate.validateChange(ALLOCATION, new AllocationSettings(true), TODAY, "m")).isEmpty();
    }

    @Test
    @Tag("D-117")
    void rejectionAndWithdrawalFreeTheTopicAndTheRequesterIsNotified() {
        authenticateAs(administrative);
        Long rejected = propose(ALLOCATION, false, DECEMBER_FIRST);
        authenticateAs(director);
        rejectRequest.reject(rejected, "no corresponde");
        assertThat(versionOf(rejected).getStatus()).isEqualTo(ConfigurationVersionStatus.REJECTED);
        assertThat(versionOf(rejected).getDecisionReason()).isEqualTo("no corresponde");
        await().until(() -> count("select count(*) from notification where recipient_id = ? and type = 'CONFIGURATION_CHANGE_REJECTED'", administrative.getId()) == 1);

        authenticateAs(administrative);
        Long withdrawn = propose(ALLOCATION, false, DECEMBER_FIRST);
        withdrawRequest.withdraw(withdrawn);
        assertThat(versionOf(withdrawn).getStatus()).isEqualTo(ConfigurationVersionStatus.WITHDRAWN);
        propose(ALLOCATION, true, DECEMBER_FIRST);
    }

    @Test
    @Tag("D-180")
    void undecidedProposalExpiresOnItsStartDateAndFreesTheTopic() {
        authenticateAs(administrative);
        Long requestId = propose(ALLOCATION, false, DECEMBER_FIRST);
        MutableClock.INSTANCE.setDate(DECEMBER_FIRST.minusDays(1));
        expirer.expireDueRequests();
        assertThat(versionOf(requestId).getStatus()).isEqualTo(ConfigurationVersionStatus.PROPOSED);

        MutableClock.INSTANCE.setDate(DECEMBER_FIRST);
        expirer.expireDueRequests();

        assertThat(versionOf(requestId).getStatus()).isEqualTo(ConfigurationVersionStatus.EXPIRED);
        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus()).isEqualTo(AuthorizationRequestStatus.EXPIRED);
        await().until(() -> count("select count(*) from notification where recipient_id = ? and type = 'AUTHORIZATION_REQUEST_EXPIRED'", administrative.getId()) >= 1);
        propose(ALLOCATION, true, DECEMBER_FIRST.plusDays(30));
    }

    @Test
    @Tag("D-180")
    @Tag("D-002")
    void approvingOnTheStartDayBeforeTheExpirationRunIsRejectedAndTheProposalIsClosed() {
        authenticateAs(administrative);
        Long requestId = propose(ALLOCATION, false, DECEMBER_FIRST);
        MutableClock.INSTANCE.setDate(DECEMBER_FIRST);
        authenticateAs(director);

        assertThatThrownBy(() -> approveRequest.approve(requestId, "tarde")).isInstanceOf(ProposalExpiredException.class);

        assertThat(requestRepository.findById(requestId).orElseThrow().getResumeOutcome()).isEqualTo(AuthorizationResumeOutcome.REJECTED_ON_RESUME);
        ConfigurationVersion version = versionOf(requestId);
        assertThat(version.getStatus()).isEqualTo(ConfigurationVersionStatus.REJECTED);
        assertThat(version.getDecisionReason()).startsWith("Rechazada al aprobar");
    }

    @Test
    @Tag("D-118")
    @Tag("D-210")
    @Tag("D-095")
    void latePaymentSettingsCreatedThroughTheLegacyUseCaseAreReadByEffectiveDate() {
        authenticateAs(director);
        createLatePayment.create(new CreateLatePaymentSettingsRequest(3, Percentage.ofFraction(new BigDecimal("0.05")), 30, 2, TODAY));
        createLatePayment.create(new CreateLatePaymentSettingsRequest(4, Percentage.ofFraction(new BigDecimal("0.07")), 40, 3, DECEMBER_FIRST));

        var today = reader.findLatePaymentSettingsEffectiveOn(TODAY).orElseThrow();
        var december = reader.findLatePaymentSettingsEffectiveOn(DECEMBER_FIRST).orElseThrow();
        assertThat(today.gracePeriodDays()).isEqualTo(3);
        assertThat(today.monthlyLateFeeRate()).isEqualTo(Percentage.ofFraction(new BigDecimal("0.05")));
        assertThat(today.lateFeeCapMonths()).isEqualTo(12);
        assertThat(december.gracePeriodDays()).isEqualTo(4);
        assertThat(december.suspensionAfterInvoiceCount()).isEqualTo(3);
    }

    @Test
    @Tag("D-225")
    void administrativeWithdrawsOwnPendingProposalAndTheTopicAcceptsANewOne() {
        authenticateAs(administrative);
        Long proposed = propose(ALLOCATION, false, DECEMBER_FIRST);
        assertThat(versionOf(proposed).getStatus()).isEqualTo(ConfigurationVersionStatus.PROPOSED);
        withdrawRequest.withdraw(proposed);
        assertThat(versionOf(proposed).getStatus()).isEqualTo(ConfigurationVersionStatus.WITHDRAWN);
        assertThat(count("select count(*) from configuration_version_status_change where configuration_version_id = ? and to_status = 'WITHDRAWN' and actor_person_id = ?",
                versionOf(proposed).getId(), administrative.getId())).isEqualTo(1);
        Long replacement = propose(ALLOCATION, true, DECEMBER_FIRST);
        assertThat(versionOf(replacement).getStatus()).isEqualTo(ConfigurationVersionStatus.PROPOSED);
    }

    @Test
    @Tag("D-225")
    void anotherAdministrativeCannotWithdrawAProposalThatIsNotTheirs() {
        authenticateAs(administrative);
        Long proposed = propose(ALLOCATION, false, DECEMBER_FIRST);
        Person other = persistPerson("administrative.other", "ADMINISTRATIVO");
        authenticateAs(other);
        assertThatThrownBy(() -> withdrawRequest.withdraw(proposed)).isInstanceOf(AuthorizationRequestAccessDeniedException.class);
        assertThat(versionOf(proposed).getStatus()).isEqualTo(ConfigurationVersionStatus.PROPOSED);
    }

    @Test
    @Tag("D-225")
    void aProposalAlreadyApprovedCannotBeWithdrawn() {
        authenticateAs(administrative);
        Long proposed = propose(ALLOCATION, false, DECEMBER_FIRST);
        authenticateAs(director);
        approveRequest.approve(proposed, "de acuerdo");
        authenticateAs(administrative);
        assertThatThrownBy(() -> withdrawRequest.withdraw(proposed)).isInstanceOf(RuntimeException.class);
        assertThat(versionOf(proposed).getStatus()).isEqualTo(ConfigurationVersionStatus.SCHEDULED);
    }

    @Test
    @Tag("D-225")
    void aDirectorPublicationIsNeverWithdrawableBecauseItHasNoPendingRequest() {
        authenticateAs(director);
        ConfigurationVersion published = publish(ALLOCATION, true, DECEMBER_FIRST);
        assertThat(published.getAuthorizationRequestId()).isNull();
        assertThat(published.getStatus()).isEqualTo(ConfigurationVersionStatus.SCHEDULED);
    }
}
