package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.dto.CreateStaffRequest;
import com.red_escuela.backend.identity.dto.UpdateStaffRequest;
import com.red_escuela.backend.identity.dto.WithdrawStudentRequest;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.entity.WithdrawalReason;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.specialist.ContactChangeRecorder;
import com.red_escuela.backend.identity.specialist.IdentityAuditRecorder;
import com.red_escuela.backend.identity.specialist.SessionRevoker;
import com.red_escuela.backend.identity.usecase.staff.CreateStaffUseCase;
import com.red_escuela.backend.identity.usecase.staff.UpdateStaffCommand;
import com.red_escuela.backend.identity.usecase.staff.UpdateStaffUseCase;
import com.red_escuela.backend.identity.usecase.staff.WithdrawStaffUseCase;
import com.red_escuela.backend.identity.usecase.student.WithdrawStudentCommand;
import com.red_escuela.backend.identity.usecase.student.WithdrawStudentService;
import com.red_escuela.backend.identity.usecase.student.WithdrawStudentUseCase;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.AtomicityTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

@SpringBootTest
@ActiveProfiles("test")
class StaffStudentTransactionBoundaryIT extends AbstractIntegrationTest {

    private static final String PREFIX = "txb";

    @Autowired
    private CreateStaffUseCase createStaffUseCase;

    @Autowired
    private UpdateStaffUseCase updateStaffUseCase;

    @Autowired
    private WithdrawStaffUseCase withdrawStaffUseCase;

    @Autowired
    private WithdrawStudentUseCase withdrawStudentUseCase;

    @Autowired
    private WithdrawStudentService plainWithdrawStudentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private SessionRevoker sessionRevoker;

    @MockitoSpyBean
    private IdentityAuditRecorder identityAuditRecorder;

    @MockitoSpyBean
    private ContactChangeRecorder contactChangeRecorder;

    @MockitoSpyBean
    private AuditTrail auditTrail;

    private final List<Long> institutionIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        reset(sessionRevoker, identityAuditRecorder, contactChangeRecorder, auditTrail);
        String persons = "select id from person where username like '" + PREFIX + "%'";
        jdbcTemplate.update("delete from audit_log where actor_person_id in (" + persons + ") or entity_id in (" + persons + ")");
        jdbcTemplate.update("delete from audit_log where entity_type = 'STUDENT' and entity_id in (select id from student where person_id in (" + persons + "))");
        jdbcTemplate.update("delete from student_status_change where student_id in (select id from student where person_id in (" + persons + "))");
        jdbcTemplate.update("delete from identity_contact_change_log where person_id in (" + persons + ") or actor_person_id in (" + persons + ")");
        jdbcTemplate.update("delete from email_verification_token where person_id in (" + persons + ")");
        jdbcTemplate.update("delete from password_reset_token where person_id in (" + persons + ")");
        jdbcTemplate.update("delete from refresh_token where person_id in (" + persons + ")");
        jdbcTemplate.update("delete from student where person_id in (" + persons + ")");
        jdbcTemplate.update("delete from person_role where person_id in (" + persons + ")");
        jdbcTemplate.update("delete from person where username like '" + PREFIX + "%'");
        institutionIds.forEach(id -> jdbcTemplate.update("delete from institution where id = ?", id));
        institutionIds.clear();
    }

    private Person persistPerson(String username, String dni, StatusType status, String roleName) {
        return new TransactionTemplate(transactionManager).execute(tx -> {
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            person.setName("Nombre");
            person.setLastname("Apellido");
            person.setUsername(username);
            person.setDni(dni);
            person.setEmail(username + "@example.com");
            person.setAuthProvider(AuthProvider.LOCAL);
            person.setAddress(address);
            person.setStatus(status);
            if (roleName != null) {
                Role role = roleRepository.findByName(roleName).orElseThrow();
                person.getRoles().add(role);
            }
            entityManager.persist(person);
            entityManager.flush();
            return person;
        });
    }

    private Student persistStudent(String username, String dni) {
        Person person = persistPerson(username, dni, StatusType.ACTIVE, null);
        return new TransactionTemplate(transactionManager).execute(tx -> {
            Institution institution = new Institution();
            institution.setName("Instituto " + username);
            entityManager.persist(institution);
            institutionIds.add(institution.getId());
            Student student = new Student();
            student.setPerson(entityManager.getReference(Person.class, person.getId()));
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            entityManager.flush();
            return student;
        });
    }

    private void authenticateAs(Person actor) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(actor.getId().toString(), null));
    }

    private void seedLiveSession(Long personId, String tokenHash) {
        jdbcTemplate.update("insert into refresh_token (person_id, token_hash, expires_at, revoked, created_at, grace_used) "
                + "values (?, ?, now() + interval '1 day', false, now(), false)", personId, tokenHash);
    }

    private int liveSessions(Long personId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from refresh_token where person_id = ? and revoked = false and grace_used = false",
                Integer.class, personId);
    }

    private int tokenVersion(Long personId) {
        return jdbcTemplate.queryForObject("select token_version from person where id = ?", Integer.class, personId);
    }

    private String personStatus(Long personId) {
        return jdbcTemplate.queryForObject("select status from person where id = ?", String.class, personId);
    }

    @Test
    void createStaffFailingWhileAuditingLeavesNoPersonRoleOrResetCode() {
        authenticateAs(persistPerson(PREFIX + ".dir.create", "79990101", StatusType.ACTIVE, "DIRECTOR"));
        CreateStaffRequest request = new CreateStaffRequest("Ana", "Perez", PREFIX + ".new@example.com", "79990102",
                DocumentType.OTHER, null, null, null, null, null, null, null, "ADMINISTRATIVO");

        AtomicityTestSupport.observing(jdbcTemplate, "person", "person_role", "address", "password_reset_token", "audit_log")
                .assertNothingPersistedWhenFailingAtStep(1, steps -> {
                    doAnswer(invocation -> {
                        steps.run(() -> {
                        });
                        return null;
                    }).when(identityAuditRecorder).recordStaffCreated(any(), anyString());

                    createStaffUseCase.create(request);
                });
    }

    @Test
    void updateStaffFailingAfterTheEmailChangeRequestLeavesNoPendingTokenNorPhone() {
        authenticateAs(persistPerson(PREFIX + ".adm.update", "79990111", StatusType.ACTIVE, "ADMINISTRATIVO"));
        Person target = persistPerson(PREFIX + ".doc.update", "79990112", StatusType.ACTIVE, "DOCENTE");
        doAnswer(invocation -> {
            throw new IllegalStateException("contact change log failed");
        }).when(contactChangeRecorder).recordChange(anyLong(), anyLong(), any(), any(), any());
        UpdateStaffRequest request = new UpdateStaffRequest(PREFIX + ".changed@example.com", "555", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> updateStaffUseCase.update(new UpdateStaffCommand(target.getId(), request)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from email_verification_token where person_id = ?", Integer.class, target.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from identity_contact_change_log where person_id = ?", Integer.class, target.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select phone_number from person where id = ?", String.class, target.getId())).isNull();
    }

    @Test
    void withdrawStaffFailingWhileRevokingSessionsKeepsTheStaffActiveAndUnaudited() {
        authenticateAs(persistPerson(PREFIX + ".adm.withdraw", "79990121", StatusType.ACTIVE, "ADMINISTRATIVO"));
        Person target = persistPerson(PREFIX + ".doc.withdraw", "79990122", StatusType.ACTIVE, "DOCENTE");
        doAnswer(invocation -> {
            throw new IllegalStateException("session revocation failed");
        }).when(sessionRevoker).revokeAllSessions(any());

        assertThatThrownBy(() -> withdrawStaffUseCase.withdraw(target.getId()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(personStatus(target.getId())).isEqualTo(StatusType.ACTIVE.name());
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_log where entity_id = ? and action = 'STAFF_WITHDRAWN'", Integer.class, target.getId())).isZero();
    }

    @Test
    void withdrawStudentFailingWhileRevokingSessionsKeepsTheStudentActive() {
        authenticateAs(persistPerson(PREFIX + ".adm.student", "79990131", StatusType.ACTIVE, "ADMINISTRATIVO"));
        Student student = persistStudent(PREFIX + ".alu.plain", "79990132");
        doAnswer(invocation -> {
            throw new IllegalStateException("session revocation failed");
        }).when(sessionRevoker).revokeAllSessions(any());

        assertThatThrownBy(() -> plainWithdrawStudentService.withdraw(new WithdrawStudentCommand(student.getId(),
                new WithdrawStudentRequest(WithdrawalReason.ABANDONO, "x"))))
                .isInstanceOf(IllegalStateException.class);

        assertThat(personStatus(student.getPerson().getId())).isEqualTo(StatusType.ACTIVE.name());
        assertThat(jdbcTemplate.queryForObject(
                "select withdrawal_reason from student where id = ?", String.class, student.getId())).isNull();
    }

    @Test
    void guardedStudentWithdrawalFailingWhileAuditingLeavesNoStatusChangeNorWithdrawal() {
        authenticateAs(persistPerson(PREFIX + ".adm.guarded", "79990141", StatusType.ACTIVE, "ADMINISTRATIVO"));
        Student student = persistStudent(PREFIX + ".alu.guarded", "79990142");
        seedLiveSession(student.getPerson().getId(), PREFIX + "-guarded-token");
        int versionBefore = tokenVersion(student.getPerson().getId());
        WithdrawStudentCommand command = new WithdrawStudentCommand(student.getId(),
                new WithdrawStudentRequest(WithdrawalReason.ABANDONO, "dejo de cursar"));

        AtomicityTestSupport.observing(jdbcTemplate, "student_status_change", "audit_log", "refresh_token")
                .assertNothingPersistedWhenFailingAtStep(1, steps -> {
                    doAnswer(invocation -> {
                        steps.run(() -> {
                        });
                        return null;
                    }).when(identityAuditRecorder).recordStudentWithdrawn(anyLong(), any());

                    withdrawStudentUseCase.withdraw(command);
                });

        assertThat(personStatus(student.getPerson().getId())).isEqualTo(StatusType.ACTIVE.name());
        assertThat(liveSessions(student.getPerson().getId())).isEqualTo(1);
        assertThat(tokenVersion(student.getPerson().getId())).isEqualTo(versionBefore);
        assertThat(jdbcTemplate.queryForObject(
                "select withdrawal_reason from student where id = ?", String.class, student.getId())).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "select withdrawn_at from student where id = ?", java.sql.Timestamp.class, student.getId())).isNull();
    }

    @Test
    void staffWithdrawalFailingAfterRealSessionRevocationKeepsLiveSessionsAndTokenVersion() {
        authenticateAs(persistPerson(PREFIX + ".adm.live", "79990151", StatusType.ACTIVE, "ADMINISTRATIVO"));
        Person target = persistPerson(PREFIX + ".doc.live", "79990152", StatusType.ACTIVE, "DOCENTE");
        seedLiveSession(target.getId(), PREFIX + "-staff-live-token");
        int versionBefore = tokenVersion(target.getId());
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> doAnswer(invocation -> {
            throw new IllegalStateException("audit failed");
        }).when(auditTrail).record(any()));

        assertThatThrownBy(() -> withdrawStaffUseCase.withdraw(target.getId()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(personStatus(target.getId())).isEqualTo(StatusType.ACTIVE.name());
        assertThat(liveSessions(target.getId())).isEqualTo(1);
        assertThat(tokenVersion(target.getId())).isEqualTo(versionBefore);
    }

    @Test
    void studentWithdrawalFailingAfterRealSessionRevocationKeepsLiveSessionsAndTokenVersion() {
        authenticateAs(persistPerson(PREFIX + ".adm.live2", "79990161", StatusType.ACTIVE, "ADMINISTRATIVO"));
        Student student = persistStudent(PREFIX + ".alu.live", "79990162");
        Long personId = student.getPerson().getId();
        seedLiveSession(personId, PREFIX + "-student-live-token");
        int versionBefore = tokenVersion(personId);
        doAnswer(invocation -> {
            throw new IllegalStateException("status change failed");
        }).when(identityAuditRecorder).recordStudentWithdrawn(anyLong(), any());

        assertThatThrownBy(() -> withdrawStudentUseCase.withdraw(new WithdrawStudentCommand(student.getId(),
                new WithdrawStudentRequest(WithdrawalReason.ABANDONO, "x"))))
                .isInstanceOf(IllegalStateException.class);

        assertThat(personStatus(personId)).isEqualTo(StatusType.ACTIVE.name());
        assertThat(liveSessions(personId)).isEqualTo(1);
        assertThat(tokenVersion(personId)).isEqualTo(versionBefore);
    }
}
