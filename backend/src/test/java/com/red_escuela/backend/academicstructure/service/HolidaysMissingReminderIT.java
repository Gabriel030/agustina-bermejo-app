package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.billing.account.service.AbstractCurrentAccountIT;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class HolidaysMissingReminderIT extends AbstractCurrentAccountIT {

    @Autowired
    private HolidaysMissingForNextYearReminderJob job;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private EntityManager entityManager;

    private Person staffWithRole(String role) {
        Person person = newStaffPerson();
        inTransaction(() -> entityManager.find(Person.class, person.getId()).getRoles().add(roleRepository.findByName(role).orElseThrow()));
        return person;
    }

    private long reminders(Person person) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(person.getId()).stream()
                .filter(notification -> notification.getType() == NotificationType.HOLIDAYS_MISSING_FOR_NEXT_YEAR && notification.getMessage().contains("2099")).count();
    }

    @Test
    void theAdministrativeGetsOneReminderPerWeekAndTheDirectorGetsNone() {
        Person administrative = staffWithRole("ADMINISTRATIVO");
        Person director = staffWithRole("DIRECTOR");
        LocalDate windowOpens = LocalDate.of(2098, 11, 2);

        job.remindIfMissing(windowOpens);
        job.remindIfMissing(windowOpens.plusDays(2));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(reminders(administrative)).isEqualTo(1));

        job.remindIfMissing(windowOpens.plusDays(7));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(reminders(administrative)).isEqualTo(2));
        assertThat(reminders(director)).isZero();
    }
}
