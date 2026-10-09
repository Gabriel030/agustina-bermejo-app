package com.red_escuela.backend.billing.operation;

import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequest;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestAccessDeniedException;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestStatus;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationResumeOutcome;
import com.red_escuela.backend.billing.authorization.domain.IllegalAuthorizationRequestTransitionException;
import com.red_escuela.backend.billing.authorization.repository.AuthorizationRequestRepository;
import com.red_escuela.backend.billing.authorization.service.AuthorizationRequestExpirer;
import com.red_escuela.backend.billing.authorization.usecase.ApproveAuthorizationRequestUseCase;
import com.red_escuela.backend.billing.authorization.usecase.RejectAuthorizationRequestUseCase;
import com.red_escuela.backend.billing.authorization.usecase.WithdrawAuthorizationRequestUseCase;
import com.red_escuela.backend.billing.configuration.exception.InvalidLatePaymentSettingsException;
import com.red_escuela.backend.billing.dto.CreateLatePaymentSettingsRequest;
import com.red_escuela.backend.billing.events.LatePaymentSettingsCreatedEvent;
import com.red_escuela.backend.billing.usecase.latepaymentsettings.CreateLatePaymentSettingsUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.InAppNotificationChannel;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import org.awaitility.core.ConditionTimeoutException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Import(BillingOperationPipelineIntegrationTest.AuthorizationHandlerConfiguration.class)
class BillingOperationPipelineIntegrationTest extends AbstractIntegrationTest {

    static final AtomicReference<PreProcessingResult> AUTHORIZATION_DEMAND = new AtomicReference<>();

    @TestConfiguration
    static class AuthorizationHandlerConfiguration {
        @Bean
        OperationPreHandlers latePaymentTestPreHandlers() {
            PreProcessingHandler<Object> handler = (type, context, prior) ->
                    prior == PriorAuthorization.ABSENT && AUTHORIZATION_DEMAND.get() != null ? AUTHORIZATION_DEMAND.get() : PreProcessingResult.proceed();
            return new OperationPreHandlers(BillingOperationType.CREATE_LATE_PAYMENT_SETTINGS, List.of(handler));
        }
    }

    @Autowired
    private CreateLatePaymentSettingsUseCase createPolicy;
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
    private PersonRepository personRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private IncompleteEventPublications incompleteEventPublications;
    @SpyBean
    private InAppNotificationChannel inAppChannel;

    private Person director;
    private Person administrative;
    private Person teacher;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from event_publication");
        AUTHORIZATION_DEMAND.set(null);
        director = persistPerson("director", "DIRECTOR");
        administrative = persistPerson("administrative", "ADMINISTRATIVO");
        teacher = persistPerson("teacher", "DOCENTE");
    }

    @AfterEach
    void tearDown() {
        awaitQuiescence();
        List<Long> personIds = List.of(director.getId(), administrative.getId(), teacher.getId());
        for (Long personId : personIds) {
            jdbcTemplate.update("delete from authorization_request_status_change where authorization_request_id in (select id from authorization_request where requested_by_id = ?)", personId);
            jdbcTemplate.update("delete from authorization_request where requested_by_id = ?", personId);
        }
        jdbcTemplate.execute("alter table configuration_version_status_change disable trigger trg_configuration_version_status_change_append_only");
        for (Long personId : personIds) {
            jdbcTemplate.update("update configuration_version set previous_version_id = null where proposed_by_id = ?", personId);
            jdbcTemplate.update("delete from configuration_version_status_change where configuration_version_id in (select id from configuration_version where proposed_by_id = ?)", personId);
            jdbcTemplate.update("delete from configuration_version where proposed_by_id = ?", personId);
        }
        jdbcTemplate.execute("alter table configuration_version_status_change enable trigger trg_configuration_version_status_change_append_only");
        jdbcTemplate.update("delete from event_publication");
        SecurityContextHolder.clearContext();
        AUTHORIZATION_DEMAND.set(null);
        reset(inAppChannel);
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

    private CreateLatePaymentSettingsRequest request(int grace, LocalDate effectiveFrom) {
        return new CreateLatePaymentSettingsRequest(grace, Percentage.ofFraction(new BigDecimal("0.10")), 30, 2, effectiveFrom);
    }

    private long count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    private long policiesCreatedBy(Person person) {
        return count("select count(*) from configuration_version where scope = 'LATE_PAYMENT' and proposed_by_id = ?", person.getId());
    }

    private long notificationsFor(Person person, String type) {
        return count("select count(*) from notification where recipient_id = ? and type = ?", person.getId(), type);
    }

    private String eventsOfThisTestPattern() {
        List<Long> personIds = List.of(director.getId(), administrative.getId(), teacher.getId());
        String persons = String.join("|", personIds.stream().map(String::valueOf).toList());
        List<Long> requestIds = jdbcTemplate.queryForList(
                "select id from authorization_request where requested_by_id in (?, ?, ?)", Long.class, personIds.toArray());
        String pattern = "\"createdByPersonId\":(" + persons + ")[,}]";
        if (requestIds.isEmpty()) {
            return pattern;
        }
        return pattern + "|\"authorizationRequestId\":(" + String.join("|", requestIds.stream().map(String::valueOf).toList()) + ")[,}]";
    }

    private long incompletePublications() {
        return count("select count(*) from event_publication where completion_date is null and serialized_event ~ ?", eventsOfThisTestPattern());
    }

    private long allPublications() {
        return count("select count(*) from event_publication where serialized_event ~ ?", eventsOfThisTestPattern());
    }

    private boolean belongsToThisTest(EventPublication publication) {
        return publication.getEvent() instanceof LatePaymentSettingsCreatedEvent event && administrative.getId().equals(event.createdByPersonId());
    }

    private void awaitQuiescence() {
        try {
            await().atMost(Duration.ofSeconds(10)).until(() -> incompletePublications() == 0);
        } catch (ConditionTimeoutException ignored) {
        }
    }

    @Test
    @Tag("D-051")
    @Tag("D-096")
    @Tag("D-178")
    void rolledBackOperationLeavesNoRowsNorEventPublication() {
        authenticateAs(administrative);

        assertThatThrownBy(() -> createPolicy.create(request(-1, LocalDate.now()))).isInstanceOf(InvalidLatePaymentSettingsException.class);

        assertThat(policiesCreatedBy(administrative)).isZero();
        assertThat(allPublications()).isZero();
    }

    @Test
    @Tag("D-051")
    @Tag("D-178")
    @Tag("D-184")
    void aProcessedOperationNotifiesDirectorsAndAdministrativesAfterTheCommit() {
        authenticateAs(administrative);

        createPolicy.create(request(3, LocalDate.now()));

        assertThat(policiesCreatedBy(administrative)).isEqualTo(1);
        await().until(() -> notificationsFor(director, "LATE_PAYMENT_SETTINGS_CREATED") == 1
                && notificationsFor(administrative, "LATE_PAYMENT_SETTINGS_CREATED") == 1);
        assertThat(notificationsFor(teacher, "LATE_PAYMENT_SETTINGS_CREATED")).isZero();
        await().until(() -> allPublications() == 0);
    }

    @Test
    @Tag("D-051")
    @Tag("D-178")
    @Tag("D-183")
    @Tag("D-184")
    void failingPostListenerDoesNotRevertAndIsRetriedWithoutDuplicatingTheNotification() {
        doThrow(new IllegalStateException("channel down")).doCallRealMethod().when(inAppChannel).send(any(), any());
        authenticateAs(administrative);

        createPolicy.create(request(3, LocalDate.now()));

        verify(inAppChannel, timeout(5000)).send(any(), any());
        await().until(() -> incompletePublications() == 1);
        assertThat(policiesCreatedBy(administrative)).isEqualTo(1);
        assertThat(notificationsFor(director, "LATE_PAYMENT_SETTINGS_CREATED")).isZero();

        incompleteEventPublications.resubmitIncompletePublications(this::belongsToThisTest);

        await().until(() -> allPublications() == 0);
        assertThat(notificationsFor(director, "LATE_PAYMENT_SETTINGS_CREATED")).isEqualTo(1);
        assertThat(notificationsFor(administrative, "LATE_PAYMENT_SETTINGS_CREATED")).isEqualTo(1);
    }

    @Test
    @Tag("D-178")
    @Tag("D-183")
    @Tag("D-184")
    void theSameEventDeliveredTwiceNotifiesOnce() {
        LatePaymentSettingsCreatedEvent event = LatePaymentSettingsCreatedEvent.createdFor(999L, LocalDate.now(), administrative.getId());

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
        await().until(() -> notificationsFor(director, "LATE_PAYMENT_SETTINGS_CREATED") >= 1);
        long afterFirst = notificationsFor(director, "LATE_PAYMENT_SETTINGS_CREATED");
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
        await().until(() -> allPublications() == 0);

        assertThat(notificationsFor(director, "LATE_PAYMENT_SETTINGS_CREATED")).isEqualTo(afterFirst);
    }

    @Test
    @Tag("D-051")
    @Tag("D-176")
    void pendingAuthorizationDoesNotProcessAndApprovalResumesTheOperation() {
        AUTHORIZATION_DEMAND.set(PreProcessingResult.requireAuthorization(AuthorizationRole.DIRECTOR, "Cambio de mora propuesto"));
        authenticateAs(administrative);

        AuthorizationPendingException pending = catchPending(() -> createPolicy.create(request(5, LocalDate.now())));

        assertThat(policiesCreatedBy(administrative)).isZero();
        AuthorizationRequest opened = requestRepository.findById(pending.authorizationRequestId()).orElseThrow();
        assertThat(opened.getStatus()).isEqualTo(AuthorizationRequestStatus.PENDING);
        assertThat(opened.getOperationType()).isEqualTo(BillingOperationType.CREATE_LATE_PAYMENT_SETTINGS);
        assertThat(opened.getOperationPayload()).contains("\"gracePeriodDays\"");
        await().until(() -> notificationsFor(director, "AUTHORIZATION_REQUESTED") >= 1);
        assertThat(notificationsFor(administrative, "AUTHORIZATION_REQUESTED")).isZero();
        assertThat(notificationsFor(director, "LATE_PAYMENT_SETTINGS_CREATED")).isZero();

        authenticateAs(director);
        AuthorizationRequest approved = approveRequest.approve(opened.getId(), "de acuerdo");

        assertThat(approved.getStatus()).isEqualTo(AuthorizationRequestStatus.APPROVED);
        assertThat(approved.getResumeOutcome()).isEqualTo(AuthorizationResumeOutcome.PROCESSED);
        assertThat(policiesCreatedBy(administrative)).isEqualTo(1);
        assertThat(policiesCreatedBy(director)).isZero();
        await().until(() -> notificationsFor(director, "LATE_PAYMENT_SETTINGS_CREATED") >= 1);
    }

    @Test
    @Tag("D-051")
    @Tag("D-176")
    void approvalOfARequestWhoseOperationIsNoLongerValidIsKeptAsRejectedOnResume() {
        AUTHORIZATION_DEMAND.set(PreProcessingResult.requireAuthorization(AuthorizationRole.DIRECTOR, "r"));
        authenticateAs(administrative);
        MutableClock.INSTANCE.setDate(LocalDate.of(2031, 3, 1));
        AuthorizationPendingException pending = catchPending(() -> createPolicy.create(request(5, LocalDate.of(2031, 3, 1))));
        MutableClock.INSTANCE.setDate(LocalDate.of(2031, 3, 10));
        authenticateAs(director);

        assertThatThrownBy(() -> approveRequest.approve(pending.authorizationRequestId(), "ok")).isInstanceOf(InvalidLatePaymentSettingsException.class);

        AuthorizationRequest request = requestRepository.findById(pending.authorizationRequestId()).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(AuthorizationRequestStatus.APPROVED);
        assertThat(request.getResumeOutcome()).isEqualTo(AuthorizationResumeOutcome.REJECTED_ON_RESUME);
        assertThat(policiesCreatedBy(administrative)).isZero();
    }

    @Test
    @Tag("D-176")
    void onlyTheRequiredRoleDecidesAndOnlyTheRequesterWithdraws() {
        AUTHORIZATION_DEMAND.set(PreProcessingResult.requireAuthorization(AuthorizationRole.DIRECTOR, "r"));
        authenticateAs(administrative);
        Long id = catchPending(() -> createPolicy.create(request(5, LocalDate.now()))).authorizationRequestId();

        assertThatThrownBy(() -> approveRequest.approve(id, "me apruebo")).isInstanceOf(AuthorizationRequestAccessDeniedException.class);
        assertThatThrownBy(() -> rejectRequest.reject(id, "no")).isInstanceOf(AuthorizationRequestAccessDeniedException.class);
        authenticateAs(director);
        assertThatThrownBy(() -> withdrawRequest.withdraw(id)).isInstanceOf(AuthorizationRequestAccessDeniedException.class);
        assertThat(requestRepository.findById(id).orElseThrow().getStatus()).isEqualTo(AuthorizationRequestStatus.PENDING);

        authenticateAs(administrative);
        assertThat(withdrawRequest.withdraw(id).getStatus()).isEqualTo(AuthorizationRequestStatus.WITHDRAWN);
        authenticateAs(director);
        assertThatThrownBy(() -> approveRequest.approve(id, "tarde")).isInstanceOf(IllegalAuthorizationRequestTransitionException.class);
        assertThat(policiesCreatedBy(administrative)).isZero();
    }

    @Test
    @Tag("D-176")
    void aRejectedRequestNeverProcessesTheOperation() {
        AUTHORIZATION_DEMAND.set(PreProcessingResult.requireAuthorization(AuthorizationRole.DIRECTOR, "r"));
        authenticateAs(administrative);
        Long id = catchPending(() -> createPolicy.create(request(5, LocalDate.now()))).authorizationRequestId();
        authenticateAs(director);

        AuthorizationRequest rejected = rejectRequest.reject(id, "no corresponde");

        assertThat(rejected.getStatus()).isEqualTo(AuthorizationRequestStatus.REJECTED);
        assertThat(rejected.getDecisionReason()).isEqualTo("no corresponde");
        assertThat(rejected.getResumeOutcome()).isNull();
        assertThat(policiesCreatedBy(administrative)).isZero();
    }

    @Test
    @Tag("D-180")
    void aPendingRequestWhoseDateArrivesExpiresAndTellsTheRequester() {
        MutableClock.INSTANCE.setDate(LocalDate.of(2031, 11, 1));
        AUTHORIZATION_DEMAND.set(PreProcessingResult.requireAuthorizationUntil(AuthorizationRole.DIRECTOR, "r", LocalDate.of(2031, 12, 1)));
        authenticateAs(administrative);
        Long expiring = catchPending(() -> createPolicy.create(request(5, LocalDate.of(2031, 12, 1)))).authorizationRequestId();
        Long stillOpen = catchPending(() -> createPolicy.create(request(6, LocalDate.of(2031, 12, 1)))).authorizationRequestId();
        jdbcTemplate.update("update authorization_request set expires_on = ? where id = ?", LocalDate.of(2032, 1, 1), stillOpen);

        MutableClock.INSTANCE.setDate(LocalDate.of(2031, 11, 30));
        long expiredBefore = expirer.expireDueRequests();
        MutableClock.INSTANCE.setDate(LocalDate.of(2031, 12, 1));
        long expiredOnTheDate = expirer.expireDueRequests();

        assertThat(expiredBefore).isZero();
        assertThat(expiredOnTheDate).isGreaterThanOrEqualTo(1);
        assertThat(requestRepository.findById(expiring).orElseThrow().getStatus()).isEqualTo(AuthorizationRequestStatus.EXPIRED);
        assertThat(requestRepository.findById(stillOpen).orElseThrow().getStatus()).isEqualTo(AuthorizationRequestStatus.PENDING);
        await().until(() -> notificationsFor(director, "AUTHORIZATION_REQUEST_EXPIRED") >= 1);
        assertThat(count("select count(*) from authorization_request_status_change where authorization_request_id = ? and actor_process is not null", expiring)).isEqualTo(1);
    }

    private AuthorizationPendingException catchPending(Runnable action) {
        AtomicReference<AuthorizationPendingException> caught = new AtomicReference<>();
        try {
            action.run();
        } catch (AuthorizationPendingException e) {
            caught.set(e);
        }
        assertThat(caught.get()).as("the operation should have been left awaiting authorization").isNotNull();
        return caught.get();
    }
}
