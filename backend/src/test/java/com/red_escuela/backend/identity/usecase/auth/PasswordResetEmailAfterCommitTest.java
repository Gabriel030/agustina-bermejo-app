package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.service.EmailService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class PasswordResetEmailAfterCommitTest extends AbstractIntegrationTest {
    @Autowired
    private RequestPasswordResetUseCase requestPasswordResetUseCase;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private EmailService emailService;

    private String email;
    private Long personId;
    private Long addressId;

    @BeforeEach
    void persistPerson() {
        email = "after-commit-" + System.nanoTime() + "@example.com";
        transactionTemplate.executeWithoutResult(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            person.setUsername(email);
            person.setEmail(email);
            person.setAuthProvider(AuthProvider.LOCAL);
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            entityManager.persist(person);
            personId = person.getId();
            addressId = address.getId();
        });
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from password_reset_token where person_id = ?", personId);
        jdbcTemplate.update("delete from person where id = ?", personId);
        jdbcTemplate.update("delete from address where id = ?", addressId);
    }

    @Test
    void theResetCodeIsEmailedOnlyAfterTheTransactionCommits() {
        transactionTemplate.executeWithoutResult(status -> {
            requestPasswordResetUseCase.requestReset(email);
            verify(emailService, never()).sendPasswordResetCode(any(), anyString());
        });

        verify(emailService).sendPasswordResetCode(any(Person.class), anyString());
    }

    @Test
    void noEmailIsSentWhenTheTransactionRollsBack() {
        transactionTemplate.executeWithoutResult(status -> {
            requestPasswordResetUseCase.requestReset(email);
            status.setRollbackOnly();
        });

        verify(emailService, never()).sendPasswordResetCode(any(), anyString());
    }
}
