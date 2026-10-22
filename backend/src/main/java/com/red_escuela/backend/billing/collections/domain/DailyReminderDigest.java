package com.red_escuela.backend.billing.collections.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record DailyReminderDigest(Long studentId, LocalDate businessDate, List<ReminderMessage> inAppMessages, List<ReminderMessage> emailMessages,
                                  List<SuppressedReminder> suppressed) {

    public DailyReminderDigest {
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(businessDate, "businessDate");
        inAppMessages = List.copyOf(inAppMessages);
        emailMessages = List.copyOf(emailMessages);
        suppressed = List.copyOf(suppressed);
    }

    public boolean hasNothingToSend() {
        return inAppMessages.isEmpty() && emailMessages.isEmpty();
    }
}
