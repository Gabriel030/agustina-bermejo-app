package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.dto.ChangePasswordRequest;
import com.red_escuela.backend.identity.dto.UpdateOwnProfileRequest;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.specialist.OwnDocumentChanger;
import com.red_escuela.backend.identity.specialist.SessionRevoker;
import com.red_escuela.backend.identity.usecase.person.ChangeOwnPasswordUseCase;
import com.red_escuela.backend.identity.usecase.person.UpdateOwnProfileUseCase;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

@SpringBootTest
@ActiveProfiles("test")
class PersonTransactionBoundaryIT extends AbstractIntegrationTest {

    private static final String USERNAME = "tx.person.boundary";
    private static final String PASSWORD = "Correcta123";

    @Autowired
    private ChangeOwnPasswordUseCase changeOwnPasswordUseCase;

    @Autowired
    private UpdateOwnProfileUseCase updateOwnProfileUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private SessionRevoker sessionRevoker;

    @MockitoSpyBean
    private OwnDocumentChanger ownDocumentChanger;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        reset(sessionRevoker, ownDocumentChanger);
        jdbcTemplate.update("delete from identity_contact_change_log where person_id in (select id from person where username = ?)", USERNAME);
        jdbcTemplate.update("delete from email_verification_token where person_id in (select id from person where username = ?)", USERNAME);
        jdbcTemplate.update("delete from person_role where person_id in (select id from person where username = ?)", USERNAME);
        jdbcTemplate.update("delete from person where username = ?", USERNAME);
    }

    private Long persistPersonAndAuthenticate() {
        Long personId = new TransactionTemplate(transactionManager).execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            person.setName("Nombre");
            person.setLastname("Apellido");
            person.setUsername(USERNAME);
            person.setDni("79990002");
            person.setEmail(USERNAME + "@example.com");
            person.setPhoneNumber("1111111");
            person.setPassword(passwordEncoder.encode(PASSWORD));
            person.setAuthProvider(AuthProvider.LOCAL);
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            entityManager.persist(person);
            entityManager.flush();
            return person.getId();
        });
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(personId.toString(), null, List.of()));
        return personId;
    }

    @Test
    void passwordChangeFailingWhileRevokingSessionsKeepsThePreviousPassword() {
        Long personId = persistPersonAndAuthenticate();
        String passwordBefore = jdbcTemplate.queryForObject("select password from person where id = ?", String.class, personId);
        doAnswer(invocation -> {
            throw new IllegalStateException("session revocation failed");
        }).when(sessionRevoker).revokeAllSessions(any());

        assertThatThrownBy(() -> changeOwnPasswordUseCase.changePassword(new ChangePasswordRequest(PASSWORD, "NuevaClave456")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject("select password from person where id = ?", String.class, personId))
                .isEqualTo(passwordBefore);
    }

    @Test
    void profileUpdateFailingOnTheDocumentStepLeavesNoPartialChanges() {
        Long personId = persistPersonAndAuthenticate();
        doAnswer(invocation -> {
            throw new IllegalStateException("document change failed");
        }).when(ownDocumentChanger).changeDocument(any(), any(), any(), any());

        UpdateOwnProfileRequest request = new UpdateOwnProfileRequest("otro.correo@example.com", "2222222", null,
                "79990003", DocumentType.DNI, null, null, null, null, null, null, null, PASSWORD);

        assertThatThrownBy(() -> updateOwnProfileUseCase.updateOwnProfile(request))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from email_verification_token where person_id = ?", Integer.class, personId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select phone_number from person where id = ?", String.class, personId)).isEqualTo("1111111");
        assertThat(jdbcTemplate.queryForObject(
                "select dni from person where id = ?", String.class, personId)).isEqualTo("79990002");
    }
}
