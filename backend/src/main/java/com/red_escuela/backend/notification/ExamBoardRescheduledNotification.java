package com.red_escuela.backend.notification;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

public record ExamBoardRescheduledNotification(Long examBoardId, String subjectName, LocalDateTime oldExamDate, LocalDateTime newExamDate,
                                               String classroom, List<Person> students, List<Person> tribunal) implements DomainNotificationEvent {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Override
    public NotificationType type() {
        return NotificationType.EXAM_BOARD_RESCHEDULED;
    }

    @Override
    public String message() {
        String room = classroom == null || classroom.isBlank() ? "" : " · " + classroom;
        return "Se reprogramó la mesa de " + subjectName + ": del " + oldExamDate.format(FORMAT) + " al " + newExamDate.format(FORMAT) + room;
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
