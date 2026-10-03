package com.red_escuela.backend.notification;

import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.identity.entity.Person;

import java.util.List;
import java.util.Objects;

final class DirectorApprovalRequestRecipients {

    private DirectorApprovalRequestRecipients() {
    }

    static List<NotificationRecipient> of(DirectorApprovalRequest request) {
        Person student = request.student();
        Person requestedBy = request.getRequestedBy();
        if (requestedBy == null || Objects.equals(requestedBy.getId(), student.getId())) {
            return List.of(new PersonRecipient(student));
        }
        return List.of(new PersonRecipient(student), new PersonRecipient(requestedBy));
    }
}
