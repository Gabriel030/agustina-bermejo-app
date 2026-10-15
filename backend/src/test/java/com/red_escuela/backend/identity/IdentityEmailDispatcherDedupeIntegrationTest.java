package com.red_escuela.backend.identity;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.service.EmailVerificationIssuedEvent;
import com.red_escuela.backend.identity.service.PasswordResetCodeIssuedEvent;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.mail.EmailService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Tag("D-178")
@Tag("D-183")
class IdentityEmailDispatcherDedupeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockBean
    private EmailService emailService;

    private long incompletePublicationsOf(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from event_publication where completion_date is null and serialized_event like ?", Long.class, "%" + eventId + "%");
    }

    private Long persistPerson() {
        String username = "dedupe-" + System.nanoTime();
        return transactionTemplate.execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            person.setUsername(username);
            person.setEmail(username + "@example.com");
            person.setAuthProvider(AuthProvider.LOCAL);
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            entityManager.persist(person);
            return person.getId();
        });
    }

    @Test
    void thePasswordResetEmailGoesOutOnceWhenTheSameEventIsDeliveredTwice() {
        Long personId = persistPerson();
        PasswordResetCodeIssuedEvent event = PasswordResetCodeIssuedEvent.issuedFor(personId, "123456");

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
        verify(emailService, timeout(5000)).sendPasswordResetCode(any(Person.class), eq("123456"));
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));

        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(10)).until(() -> incompletePublicationsOf(event.eventId()) == 0);
        verify(emailService, times(1)).sendPasswordResetCode(any(Person.class), eq("123456"));
    }

    @Test
    void theVerificationEmailGoesOutOnceWhenTheSameEventIsDeliveredTwice() {
        Long personId = persistPerson();
        EmailVerificationIssuedEvent event = EmailVerificationIssuedEvent.issuedFor(personId, "https://example.test/verify");

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
        verify(emailService, timeout(5000)).sendEmailVerification(any(Person.class), eq("https://example.test/verify"));
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));

        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(10)).until(() -> incompletePublicationsOf(event.eventId()) == 0);
        verify(emailService, times(1)).sendEmailVerification(any(Person.class), eq("https://example.test/verify"));
    }
}
