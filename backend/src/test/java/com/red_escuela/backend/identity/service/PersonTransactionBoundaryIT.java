package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.dto.ChangePasswordRequest;
import com.red_escuela.backend.identity.dto.UpdateOwnProfileRequest;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.specialist.OwnDocumentChanger;
import com.red_escuela.backend.identity.specialist.SessionRevoker;
import com.red_escuela.backend.identity.usecase.person.ChangeOwnPasswordUseCase;
import com.red_escuela.backend.identity.usecase.person.UpdateOwnProfileUseCase;
import com.red_escuela.backend.identity.usecase.person.UploadAvatarUseCase;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.storage.FileStorageService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
    private UploadAvatarUseCase uploadAvatarUseCase;

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

    @MockitoSpyBean
    private FileStorageService fileStorageService;

    @MockitoSpyBean
    private PersonRepository personRepository;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        reset(sessionRevoker, ownDocumentChanger, personRepository);
        jdbcTemplate.queryForList("select avatar_path from person where username = ? and avatar_path like 'avatars/%_%'",
                String.class, USERNAME).stream().filter(path -> !path.equals("avatars/previous-avatar.png"))
                .forEach(fileStorageService::delete);
        reset(fileStorageService);
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
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("session revocation failed");

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
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("document change failed");

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from email_verification_token where person_id = ?", Integer.class, personId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from identity_contact_change_log where person_id = ?", Integer.class, personId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select phone_number from person where id = ?", String.class, personId)).isEqualTo("1111111");
        assertThat(jdbcTemplate.queryForObject(
                "select dni from person where id = ?", String.class, personId)).isEqualTo("79990002");
    }

    private static MockMultipartFile avatar() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
        return new MockMultipartFile("file", "a.png", "image/png", png);
    }

    @Test
    void avatarUploadFailingAfterStoringTheFileDeletesTheNewFileAndKeepsThePreviousOne() {
        Long personId = persistPersonAndAuthenticate();
        jdbcTemplate.update("update person set avatar_path = ? where id = ?", "avatars/previous-avatar.png", personId);
        doAnswer(invocation -> {
            throw new IllegalStateException("person save failed");
        }).when(personRepository).save(any(Person.class));

        assertThatThrownBy(() -> uploadAvatarUseCase.upload(avatar()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("person save failed");

        ArgumentCaptor<String> deleted = ArgumentCaptor.forClass(String.class);
        verify(fileStorageService, times(1)).delete(deleted.capture());
        assertThat(deleted.getValue()).startsWith("avatars/" + personId + "_");
        verify(fileStorageService, never()).delete("avatars/previous-avatar.png");
        assertThat(jdbcTemplate.queryForObject("select avatar_path from person where id = ?", String.class, personId))
                .isEqualTo("avatars/previous-avatar.png");
    }

    @Test
    void avatarUploadCommittedDeletesThePreviousFileAndKeepsTheNewOne() {
        Long personId = persistPersonAndAuthenticate();
        jdbcTemplate.update("update person set avatar_path = ? where id = ?", "avatars/previous-avatar.png", personId);

        uploadAvatarUseCase.upload(avatar());

        String stored = jdbcTemplate.queryForObject("select avatar_path from person where id = ?", String.class, personId);
        assertThat(stored).startsWith("avatars/" + personId + "_");
        verify(fileStorageService).delete("avatars/previous-avatar.png");
        verify(fileStorageService, never()).delete(stored);
    }
}
