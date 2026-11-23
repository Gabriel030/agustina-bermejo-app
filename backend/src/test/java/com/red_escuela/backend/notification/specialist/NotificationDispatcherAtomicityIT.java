package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.api.PersonRecipientFinder;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.domain.DomainNotificationEvent;
import com.red_escuela.backend.notification.domain.NotificationRecipient;
import com.red_escuela.backend.notification.entity.NotificationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class NotificationDispatcherAtomicityIT extends AbstractIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void createBusinessOperationTable() {
        jdbcTemplate.execute("create table dispatcher_atomicity_operation (id int primary key)");
    }

    @AfterEach
    void dropBusinessOperationTable() {
        jdbcTemplate.execute("drop table dispatcher_atomicity_operation");
    }

    @Test
    void dispatchFailureRevertsTheBusinessOperationThatPublishedTheNotification() {
        NotificationRecipient failing = new NotificationRecipient() {
            @Override
            public List<Person> resolve(PersonRecipientFinder personRecipientFinder) {
                throw new IllegalStateException("dispatch failed");
            }
        };

        assertThatThrownBy(() -> runBusinessOperationPublishing(notificationTo(failing)))
                .hasMessageContaining("dispatch failed");

        assertThat(operationCount()).isZero();
    }

    @Test
    void successfulDispatchCommitsTheBusinessOperationThatPublishedTheNotification() {
        NotificationRecipient nobody = new NotificationRecipient() {
            @Override
            public List<Person> resolve(PersonRecipientFinder personRecipientFinder) {
                return List.of();
            }
        };

        runBusinessOperationPublishing(notificationTo(nobody));

        assertThat(operationCount()).isEqualTo(1);
    }

    private void runBusinessOperationPublishing(DomainNotificationEvent event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update("insert into dispatcher_atomicity_operation (id) values (1)");
            eventPublisher.publishEvent(event);
        });
    }

    private long operationCount() {
        return jdbcTemplate.queryForObject("select count(*) from dispatcher_atomicity_operation", Long.class);
    }

    private DomainNotificationEvent notificationTo(NotificationRecipient recipient) {
        return new DomainNotificationEvent() {
            @Override
            public NotificationType type() {
                return NotificationType.values()[0];
            }

            @Override
            public String message() {
                return "atomicity probe";
            }

            @Override
            public List<NotificationRecipient> recipients() {
                return List.of(recipient);
            }

            @Override
            public String relatedEntityType() {
                return "ATOMICITY_PROBE";
            }

            @Override
            public Long relatedEntityId() {
                return 1L;
            }
        };
    }
}
