package com.red_escuela.backend.notification.domain;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

public record ExamBoardCanceledNotification(Long examBoardId, String subjectName, LocalDateTime examDate, String reason,
                                            List<Person> students, List<Person> tribunal) implements DomainNotificationEvent {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Override
    public NotificationType type() {
        return NotificationType.EXAM_BOARD_CANCELED;
    }

    @Override
    public String message() {
        return "Se canceló la mesa de " + subjectName + " del " + examDate.format(FORMAT) + ". Motivo: " + reason;
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return Stream.concat(students.stream(), tribunal.stream()).map(person -> (NotificationRecipient) new PersonRecipient(person)).toList();
    }

    @Override
    public String relatedEntityType() {
        return "ExamBoard";
    }

    @Override
    public Long relatedEntityId() {
        return examBoardId;
    }
}
