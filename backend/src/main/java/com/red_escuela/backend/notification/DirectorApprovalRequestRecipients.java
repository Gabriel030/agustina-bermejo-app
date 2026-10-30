package com.red_escuela.backend.notification;

import com.red_escuela.backend.identity.entity.Person;

import java.util.List;
import java.util.Objects;

final class DirectorApprovalRequestRecipients {

    private DirectorApprovalRequestRecipients() {
    }

    static List<NotificationRecipient> of(Person student, Person requestedBy) {
        if (requestedBy == null || Objects.equals(requestedBy.getId(), student.getId())) {
            return List.of(new PersonRecipient(student));
        }
        return List.of(new PersonRecipient(student), new PersonRecipient(requestedBy));
    }
}
