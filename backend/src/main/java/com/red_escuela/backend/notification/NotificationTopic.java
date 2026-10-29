package com.red_escuela.backend.notification;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.PersonRepository;

import java.util.List;

public enum NotificationTopic implements NotificationRecipient {
    DIRECTOR_APPROVALS(new RoleRecipient("DIRECTOR")),
    EVALUATION_CONFIG_SUPERVISION(new RoleRecipient("DIRECTOR"), new RoleRecipient("ADMINISTRATIVO")),
    BILLING_CONFIGURATION_SUPERVISION(new RoleRecipient("DIRECTOR"), new RoleRecipient("ADMINISTRATIVO")),
    DISCOUNT_SUPERVISION(new RoleRecipient("DIRECTOR")),
    ENROLLMENT_SECRETARIAT(new RoleRecipient("ADMINISTRATIVO"), new RoleRecipient("PRECEPTOR")),
    PAYMENT_REVERSAL_SUPERVISION(new RoleRecipient("DIRECTOR"), new RoleRecipient("ADMINISTRATIVO")),
    BILLING_RUN_SUPERVISION(new RoleRecipient("DIRECTOR"), new RoleRecipient("ADMINISTRATIVO"));

    private final List<NotificationRecipient> members;

    NotificationTopic(NotificationRecipient... members) {
        this.members = List.of(members);
    }

    @Override
    public List<Person> resolve(PersonRepository personRepository) {
        return members.stream()
                .flatMap(member -> member.resolve(personRepository).stream())
                .distinct()
                .toList();
    }
}
