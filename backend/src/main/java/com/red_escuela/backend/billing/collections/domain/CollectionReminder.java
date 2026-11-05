package com.red_escuela.backend.billing.collections.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.red_escuela.backend.billing.events.PlannedReminderMessage;
import com.red_escuela.backend.shared.time.ApplicationTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Entity
@Immutable
@Table(name = "collection_reminder")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Long studentId;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "in_app_messages", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String inAppMessagesJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "email_messages", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String emailMessagesJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suppressed", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String suppressedJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static CollectionReminder recordPlannedFor(Long studentId, LocalDate businessDate, List<PlannedReminderMessage> inAppMessages,
                                                      List<PlannedReminderMessage> emailMessages, List<SuppressedReminder> suppressed) {
        if (inAppMessages.isEmpty() && emailMessages.isEmpty() && suppressed.isEmpty()) {
            throw new IllegalArgumentException("A collection reminder must carry at least one message or one suppressed notice");
        }
        CollectionReminder reminder = new CollectionReminder();
        reminder.studentId = Objects.requireNonNull(studentId, "studentId");
        reminder.businessDate = Objects.requireNonNull(businessDate, "businessDate");
        reminder.inAppMessagesJson = CollectionReminderMessagesJson.write(inAppMessages);
        reminder.emailMessagesJson = CollectionReminderMessagesJson.write(emailMessages);
        reminder.suppressedJson = CollectionReminderMessagesJson.write(suppressed);
        reminder.createdAt = ApplicationTime.now();
        return reminder;
    }

    public List<PlannedReminderMessage> getInAppMessages() {
        return CollectionReminderMessagesJson.read(inAppMessagesJson, new TypeReference<List<PlannedReminderMessage>>() {
        });
    }

    public List<PlannedReminderMessage> getEmailMessages() {
        return CollectionReminderMessagesJson.read(emailMessagesJson, new TypeReference<List<PlannedReminderMessage>>() {
        });
    }

    public List<SuppressedReminder> getSuppressed() {
        return CollectionReminderMessagesJson.read(suppressedJson, new TypeReference<List<SuppressedReminder>>() {
        });
    }
}
