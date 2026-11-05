package com.red_escuela.backend.notification;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

final class AttendanceChangeRequestMessage {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private AttendanceChangeRequestMessage() {
    }

    static String of(String studentFullName, LocalDate classDate, String previousStatusLabel, String requestedStatusLabel, String decision) {
        return "Tu solicitud de modificación de asistencia de " + studentFullName
                + " del " + classDate.format(DAY_FORMAT) + " fue " + decision + ": "
                + previousStatusLabel + " → " + requestedStatusLabel;
    }
}
