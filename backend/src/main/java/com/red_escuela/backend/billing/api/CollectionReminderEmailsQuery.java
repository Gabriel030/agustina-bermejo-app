package com.red_escuela.backend.billing.api;

import java.util.Optional;

public interface CollectionReminderEmailsQuery {

    Optional<StoredReminderEmails> findEmailsOfReminder(Long reminderId);
}
