package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.events.PlannedReminderMessage;

import java.time.LocalDate;
import java.util.List;

public record StoredReminderEmails(LocalDate businessDate, List<PlannedReminderMessage> emailMessages) {

    public StoredReminderEmails {
        emailMessages = List.copyOf(emailMessages);
    }
}
