package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestAccessDeniedException;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestStatus;
import com.red_escuela.backend.billing.authorization.domain.IllegalAuthorizationRequestTransitionException;
import com.red_escuela.backend.billing.authorization.repository.AuthorizationRequestRepository;
import com.red_escuela.backend.billing.authorization.usecase.ApproveAuthorizationRequestUseCase;
import com.red_escuela.backend.billing.authorization.usecase.WithdrawAuthorizationRequestUseCase;
import com.red_escuela.backend.billing.discount.DiscountDraftBuilder;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionStatus;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;
import com.red_escuela.backend.billing.discount.exception.DiscountClosureReasonRequiredException;
import com.red_escuela.backend.billing.discount.exception.DiscountDefinitionNotFoundException;
import com.red_escuela.backend.billing.discount.exception.IllegalDiscountDefinitionTransitionException;
import com.red_escuela.backend.billing.discount.exception.InvalidDiscountDefinitionException;
import com.red_escuela.backend.billing.discount.exception.ScholarshipOnlyForMonthlyInstallmentsException;
import com.red_escuela.backend.billing.discount.repository.DiscountDefinitionRepository;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.discount.exception.DiscountScopeOfferingNotFoundException;
import org.springframework.jdbc.UncategorizedSQLException;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Tag("D-217")
@Tag("D-223")
@Tag("D-225")
@Tag("D-036")
@Tag("D-157")
@Tag("D-129")
@Tag("D-075")
@Tag("D-097")
class DiscountDefinitionCatalogIT extends AbstractIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2031, 3, 10);

    @Autowired
    private BillingOperationPipeline pipeline;
    @Autowired
    private CreateDiscountDefinitionOperation createOperation;
    @Autowired
    private CloseDiscountDefinitionOperation closeOperation;
    @Autowired
    private DiscountDefinitionRepository repository;
    @Autowired
    private ApproveAuthorizationRequestUseCase approveRequest;
    @Autowired
    private WithdrawAuthorizationRequestUseCase withdrawRequest;
    @Autowired
    private AuthorizationRequestRepository requestRepository;
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
    private Person otherAdministrative;

    @BeforeEach
    void setUp() {
        MutableClock.INSTANCE.setDate(TODAY);
        director = persistPerson("catalog.director", "DIRECTOR");
        administrative = persistPerson("catalog.administrative", "ADMINISTRATIVO");
        otherAdministrative = persistPerson("catalog.other", "ADMINISTRATIVO");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        String people = director.getId() + "," + administrative.getId() + "," + otherAdministrative.getId();
        jdbc.execute("alter table discount_definition_status_change disable trigger trg_discount_definition_status_change_append_only");
        jdbc.update("delete from discount_definition_status_change where discount_definition_id in (select id from discount_definition where created_by_id in (" + people + "))");
        jdbc.update("delete from discount_definition where created_by_id in (" + people + ")");
        jdbc.execute("alter table discount_definition_status_change enable trigger trg_discount_definition_status_change_append_only");
        jdbc.update("delete from authorization_request_status_change where authorization_request_id in (select id from authorization_request where requested_by_id in (" + people + "))");
        jdbc.update("delete from authorization_request where requested_by_id in (" + people + ")");
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

    private DiscountDefinition createAsDirector(DiscountDefinitionDraft draft) {
        authenticateAs(director);
        BillingOperationOutcome<DiscountDefinition> outcome = pipeline.runOperation(createOperation, CreateDiscountDefinitionCommand.byPerson(draft, director.getId(), true));
        return ((BillingOperationOutcome.Processed<DiscountDefinition>) outcome).result();
    }

    private Long proposeAsAdministrative(Person proposer, DiscountDefinitionDraft draft) {
        authenticateAs(proposer);
        BillingOperationOutcome<DiscountDefinition> outcome = pipeline.runOperation(createOperation, CreateDiscountDefinitionCommand.byPerson(draft, proposer.getId(), false));
        return ((BillingOperationOutcome.AwaitingAuthorization<DiscountDefinition>) outcome).authorizationRequestId();
    }

    private long countDefinitionsNamed(String name) {
        return jdbc.queryForObject("select count(*) from discount_definition where name = ?", Long.class, name);
    }

    @Test
    void theDirectorCreatesAnActiveDefinitionWithoutAnyRequest() {
        DiscountDefinition created = createAsDirector(DiscountDraftBuilder.annualUpfront().named("Pago anual director").withPercent("15").build());

        assertThat(created.getId()).isNotNull();
        var row = jdbc.queryForMap("select status, direct_publication, authorization_request_id, created_by_id, discount_type from discount_definition where id = ?", created.getId());
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("direct_publication")).isEqualTo(true);
        assertThat(row.get("authorization_request_id")).isNull();
        assertThat(row.get("created_by_id")).isEqualTo(director.getId());
        assertThat(row.get("discount_type")).isEqualTo("ANNUAL_UPFRONT");
        assertThat(jdbc.queryForObject("select count(*) from discount_definition_status_change where discount_definition_id = ? and event = 'DEFINITION_CREATED' and to_status = 'ACTIVE' and from_status is null",
                Long.class, created.getId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from authorization_request where requested_by_id = ?", Long.class, director.getId())).isZero();
    }

    @Test
    void anAdministrativeProposalDoesNotCreateTheDefinitionUntilTheDirectorApproves() {
        Long requestId = proposeAsAdministrative(administrative, DiscountDraftBuilder.annualUpfront().named("Pago anual propuesto").withPercent("12").build());

        assertThat(countDefinitionsNamed("Pago anual propuesto")).isZero();
        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus()).isEqualTo(AuthorizationRequestStatus.PENDING);

        authenticateAs(director);
        approveRequest.approve(requestId, "de acuerdo");

        assertThat(countDefinitionsNamed("Pago anual propuesto")).isEqualTo(1L);
        var row = jdbc.queryForMap("select status, direct_publication, authorization_request_id, created_by_id from discount_definition where name = 'Pago anual propuesto'");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("direct_publication")).isEqualTo(false);
        assertThat(row.get("authorization_request_id")).isEqualTo(requestId);
        assertThat(row.get("created_by_id")).isEqualTo(administrative.getId());
        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus()).isEqualTo(AuthorizationRequestStatus.APPROVED);
    }

    @Test
    void theProposerCanWithdrawWhilePendingButNobodyElseAndNotAfterApproval() {
        Long withdrawnId = proposeAsAdministrative(administrative, DiscountDraftBuilder.annualUpfront().named("Propuesta retirada").build());

        authenticateAs(otherAdministrative);
        assertThatThrownBy(() -> withdrawRequest.withdraw(withdrawnId)).isInstanceOf(AuthorizationRequestAccessDeniedException.class);
        authenticateAs(administrative);
        withdrawRequest.withdraw(withdrawnId);
        assertThat(requestRepository.findById(withdrawnId).orElseThrow().getStatus()).isEqualTo(AuthorizationRequestStatus.WITHDRAWN);
        assertThat(countDefinitionsNamed("Propuesta retirada")).isZero();

        Long approvedId = proposeAsAdministrative(administrative, DiscountDraftBuilder.annualUpfront().named("Propuesta aprobada").build());
        authenticateAs(director);
        approveRequest.approve(approvedId, "ok");
        authenticateAs(administrative);
        assertThatThrownBy(() -> withdrawRequest.withdraw(approvedId)).isInstanceOf(IllegalAuthorizationRequestTransitionException.class);
        assertThat(countDefinitionsNamed("Propuesta aprobada")).isEqualTo(1L);
    }

    @Test
    void aScholarshipOnTheEnrollmentFeeIsRejectedBeforeAnythingIsPersisted() {
        authenticateAs(director);
        CreateDiscountDefinitionCommand command = CreateDiscountDefinitionCommand.byPerson(
                DiscountDraftBuilder.scholarship().named("Beca de matrícula").forInvoiceType(InvoiceType.ENROLLMENT_FEE).build(), director.getId(), true);

        assertThatThrownBy(() -> pipeline.runOperation(createOperation, command)).isInstanceOf(ScholarshipOnlyForMonthlyInstallmentsException.class);
        assertThat(countDefinitionsNamed("Beca de matrícula")).isZero();
    }

    @Test
    void anAdministrativeProposalThatBreaksARuleIsRejectedWithoutOpeningARequest() {
        authenticateAs(administrative);
        CreateDiscountDefinitionCommand command = CreateDiscountDefinitionCommand.byPerson(
                DiscountDraftBuilder.annualUpfront().named("Anual en cero").withPercent("0").build(), administrative.getId(), false);

        assertThatThrownBy(() -> pipeline.runOperation(createOperation, command)).isInstanceOf(InvalidDiscountDefinitionException.class);
        assertThat(jdbc.queryForObject("select count(*) from authorization_request where requested_by_id = ?", Long.class, administrative.getId())).isZero();
    }

    @Test
    void aScopeThatPointsToAMissingProgramIsNotFound() {
        authenticateAs(director);
        CreateDiscountDefinitionCommand command = CreateDiscountDefinitionCommand.byPerson(
                DiscountDraftBuilder.cohortPromotion().named("Promoción huérfana").forProgram(987654321L).build(), director.getId(), true);

        assertThatThrownBy(() -> pipeline.runOperation(createOperation, command)).isInstanceOf(DiscountScopeOfferingNotFoundException.class);
    }

    @Test
    void theDirectorClosesADefinitionDirectlyAndTheValuesStayAsTheyWere() {
        DiscountDefinition created = createAsDirector(DiscountDraftBuilder.annualUpfront().named("Para cerrar").withPercent("15").build());
        authenticateAs(director);

        pipeline.runOperation(closeOperation, new CloseDiscountDefinitionCommand(created.getId(), "Fin de campaña", director.getId(), true));

        var row = jdbc.queryForMap("select status, closed_by_id, closure_reason, name, priority, calculation::text as calculation from discount_definition where id = ?", created.getId());
        assertThat(row.get("status")).isEqualTo("CLOSED");
        assertThat(row.get("closed_by_id")).isEqualTo(director.getId());
        assertThat(row.get("closure_reason")).isEqualTo("Fin de campaña");
        assertThat(row.get("name")).isEqualTo("Para cerrar");
        assertThat(row.get("priority")).isEqualTo(1);
        assertThat(row.get("calculation").toString()).contains("PROPORTIONAL");
        assertThat(jdbc.queryForObject("select count(*) from discount_definition_status_change where discount_definition_id = ?", Long.class, created.getId())).isEqualTo(2L);
    }

    @Test
    void anAdministrativeClosureIsOnlyExecutedWhenTheDirectorApproves() {
        DiscountDefinition created = createAsDirector(DiscountDraftBuilder.annualUpfront().named("Cierre propuesto").build());
        authenticateAs(administrative);

        BillingOperationOutcome<DiscountDefinition> outcome = pipeline.runOperation(closeOperation,
                new CloseDiscountDefinitionCommand(created.getId(), "Ya no se usa", administrative.getId(), false));

        Long requestId = ((BillingOperationOutcome.AwaitingAuthorization<DiscountDefinition>) outcome).authorizationRequestId();
        assertThat(repository.findById(created.getId()).orElseThrow().getStatus()).isEqualTo(DiscountDefinitionStatus.ACTIVE);

        authenticateAs(director);
        approveRequest.approve(requestId, "ok");

        DiscountDefinition closed = repository.findById(created.getId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(DiscountDefinitionStatus.CLOSED);
        assertThat(closed.getClosedById()).isEqualTo(director.getId());
        assertThat(closed.getClosureReason()).isEqualTo("Ya no se usa");
    }

    @Test
    void closingARequiresAReasonAndAnExistingDefinition() {
        DiscountDefinition created = createAsDirector(DiscountDraftBuilder.annualUpfront().named("Sin motivo").build());
        authenticateAs(director);

        assertThatThrownBy(() -> pipeline.runOperation(closeOperation, new CloseDiscountDefinitionCommand(created.getId(), " ", director.getId(), true)))
                .isInstanceOf(DiscountClosureReasonRequiredException.class);
        assertThatThrownBy(() -> pipeline.runOperation(closeOperation, new CloseDiscountDefinitionCommand(987654321L, "motivo", director.getId(), true)))
                .isInstanceOf(DiscountDefinitionNotFoundException.class);
        assertThat(repository.findById(created.getId()).orElseThrow().getStatus()).isEqualTo(DiscountDefinitionStatus.ACTIVE);
    }

    @Test
    void closingAClosedDefinitionIsAConflict() {
        DiscountDefinition created = createAsDirector(DiscountDraftBuilder.annualUpfront().named("Cerrada dos veces").build());
        authenticateAs(director);
        pipeline.runOperation(closeOperation, new CloseDiscountDefinitionCommand(created.getId(), "primero", director.getId(), true));

        assertThatThrownBy(() -> pipeline.runOperation(closeOperation, new CloseDiscountDefinitionCommand(created.getId(), "segundo", director.getId(), true)))
                .isInstanceOf(IllegalDiscountDefinitionTransitionException.class);
        assertThat(jdbc.queryForObject("select count(*) from discount_definition_status_change where discount_definition_id = ?", Long.class, created.getId())).isEqualTo(2L);
    }

    @Test
    void twoSimultaneousClosuresLeaveOneClosureAndOneConflict() throws Exception {
        DiscountDefinition created = createAsDirector(DiscountDraftBuilder.annualUpfront().named("Cierre concurrente").build());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<String>> closures = List.of(closureAttempt(created.getId(), "uno", start), closureAttempt(created.getId(), "dos", start));
            List<Future<String>> futures = closures.stream().map(executor::submit).toList();
            start.countDown();
            List<String> outcomes = List.of(futures.get(0).get(), futures.get(1).get());

            assertThat(outcomes).containsExactlyInAnyOrder("CLOSED", "IllegalDiscountDefinitionTransitionException");
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from discount_definition_status_change where discount_definition_id = ? and event = 'DEFINITION_CLOSED'", Long.class, created.getId())).isEqualTo(1L);
    }

    private Callable<String> closureAttempt(Long definitionId, String reason, CountDownLatch start) {
        return () -> {
            authenticateAs(director);
            start.await();
            try {
                pipeline.runOperation(closeOperation, new CloseDiscountDefinitionCommand(definitionId, reason, director.getId(), true));
                return "CLOSED";
            } catch (RuntimeException rejection) {
                return rejection.getClass().getSimpleName();
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    @Test
    void theValuesOfADefinitionCannotBeChangedInTheDatabase() {
        DiscountDefinition created = createAsDirector(DiscountDraftBuilder.annualUpfront().named("Inmutable").build());

        assertThatThrownBy(() -> jdbc.update("update discount_definition set priority = 5 where id = ?", created.getId())).isInstanceOf(UncategorizedSQLException.class);
        assertThatThrownBy(() -> jdbc.update("update discount_definition set name = 'otro' where id = ?", created.getId())).isInstanceOf(UncategorizedSQLException.class);
        assertThat(jdbc.update("update discount_definition set quota_exhausted_notified_at = now() where id = ?", created.getId())).isEqualTo(1);
    }

    @Test
    void theStatusHistoryIsAppendOnly() {
        DiscountDefinition created = createAsDirector(DiscountDraftBuilder.annualUpfront().named("Historia").build());

        assertThatThrownBy(() -> jdbc.update("update discount_definition_status_change set reason = 'x' where discount_definition_id = ?", created.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("delete from discount_definition_status_change where discount_definition_id = ?", created.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRejectsWhatThePoliciesRejectEvenWhenInsertedDirectly() {
        String insert = "insert into discount_definition (name, discount_type, applicable_invoice_type, calculation, installments_total, available_from, priority, status, "
                + "created_by_process, created_at, direct_publication) values (?, ?, ?, '{\"type\":\"PROPORTIONAL\",\"fraction\":\"0.1\"}'::jsonb, ?::integer, date '2031-03-01', ?, 'ACTIVE', 'test', now(), true)";

        assertThatThrownBy(() -> jdbc.update(insert, "x", "SCHOLARSHIP", "ENROLLMENT_FEE", null, 1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "x", "COHORT_PROMOTION", "ENROLLMENT_FEE", 3, 1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "x", "COHORT_PROMOTION", "LATE_FEE", null, 1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "x", "COHORT_ENTRY", "ENROLLMENT_FEE", null, 1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "x", "ANNUAL_UPFRONT", "MONTHLY_INSTALLMENT", null, 1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "x", "ANNUAL_UPFRONT", "ANNUAL_UPFRONT", null, 0)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "x", "COHORT_PROMOTION", "MONTHLY_INSTALLMENT", 0, 1)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theInitialScholarshipDefinitionIsSeeded() {
        var row = jdbc.queryForMap("select discount_type, applicable_invoice_type, priority, status, direct_publication, calculation, cap_per_invoice, created_by_process "
                + "from discount_definition where created_by_process = 'DiscountCatalogSeed'");

        assertThat(row.get("discount_type")).isEqualTo(DiscountDefinitionType.SCHOLARSHIP.name());
        assertThat(row.get("applicable_invoice_type")).isEqualTo("MONTHLY_INSTALLMENT");
        assertThat(row.get("priority")).isEqualTo(1);
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("direct_publication")).isEqualTo(true);
        assertThat(row.get("calculation")).isNull();
        assertThat(row.get("cap_per_invoice")).isNull();
        assertThat(jdbc.queryForObject("select count(*) from discount_definition_status_change c join discount_definition d on d.id = c.discount_definition_id "
                + "where d.created_by_process = 'DiscountCatalogSeed' and c.event = 'DEFINITION_CREATED'", Long.class)).isEqualTo(1L);
    }
}
