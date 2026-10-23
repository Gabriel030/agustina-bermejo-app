package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.PlannedReminderInvoiceKind;
import com.red_escuela.backend.billing.events.PostWithdrawalSummaryPlannedEvent;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Tag("D-338")
class PostWithdrawalSummaryNotificationListenerIT extends AbstractIntegrationTest {
    private static final LocalDate AUGUST_SECOND = LocalDate.of(2027, 8, 2);

    @Autowired
    private PostWithdrawalSummaryNotificationListener listener;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    private Person persistPerson() {
        String dni = "6" + System.nanoTime();
        return transactionTemplate.execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            person.setName("Persona");
            person.setLastname(dni);
            person.setDni(dni);
            person.setUsername(dni);
            person.setEmail(dni + "@example.com");
            person.setEmailVerified(true);
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            entityManager.persist(person);
            return person;
        });
    }

    private static PostWithdrawalSummaryPlannedEvent eventFor(Person person) {
        PostWithdrawalSummaryPlannedEvent.Line line = new PostWithdrawalSummaryPlannedEvent.Line(PlannedReminderInvoiceKind.MONTHLY_INSTALLMENT, LocalDate.of(2027, 7, 10),
                ars(100000));
        return PostWithdrawalSummaryPlannedEvent.plannedFor(person.getId(), AUGUST_SECOND, List.of(new PostWithdrawalSummaryPlannedEvent.Block(List.of(line), ars(100000))));
    }

    private void deliver(PostWithdrawalSummaryPlannedEvent event) {
        listener.deliverSummary(event);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("select count(*) from processed_effect where event_id = ?", Long.class, event.eventId())).isEqualTo(1L));
    }

    private List<Map<String, Object>> deliveriesOf(Person person, String channel) {
        return jdbc.queryForList("select * from notification_delivery where recipient_person_id = ? and channel = ?", person.getId(), channel);
    }

    @Test
    @Tag("D-338")
    void theSummaryQueuesOneInAppAndOneMailWithTheLowestDispatchPriorityAndARepeatedEventAddsNothing() {
        Person person = persistPerson();
        PostWithdrawalSummaryPlannedEvent event = eventFor(person);

        deliver(event);
        listener.deliverSummary(event);

        List<Map<String, Object>> mails = deliveriesOf(person, "EMAIL");
        assertThat(mails).hasSize(1);
        assertThat(mails.get(0).get("kind")).isEqualTo("POST_WITHDRAWAL_SUMMARY");
        assertThat(mails.get(0).get("priority")).isEqualTo(7);
        assertThat(mails.get(0).get("status")).isEqualTo("QUEUED");
        assertThat(mails.get(0).get("scheduled_for")).hasToString("2027-08-02");
        assertThat(mails.get(0).get("subject")).isEqualTo("Resumen de tu deuda pendiente");
        assertThat(deliveriesOf(person, "IN_APP")).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from notification where recipient_id = ? and type = 'COLLECTION_REMINDER'", Long.class, person.getId())).isEqualTo(1L);
    }

    @Test
    @Tag("D-338")
    void theSummaryOfTheNextMonthIsADifferentDeliveryOfTheSameStudent() {
        Person person = persistPerson();
        deliver(eventFor(person));
        PostWithdrawalSummaryPlannedEvent september = PostWithdrawalSummaryPlannedEvent.plannedFor(person.getId(), LocalDate.of(2027, 9, 1), eventFor(person).blocks());

        deliver(september);

        assertThat(deliveriesOf(person, "EMAIL")).hasSize(2);
    }
}
