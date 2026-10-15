package com.red_escuela.backend.notification.delivery.domain;

public record DeliveryContent(String subject, String bodyText, String bodyHtml) {

    public static final int MAX_SUBJECT_LENGTH = 300;

    public DeliveryContent {
        if (bodyText == null || bodyText.isBlank()) {
            throw new IllegalArgumentException("A delivery needs a plain text body");
        }
        if (subject != null && (subject.isBlank() || subject.length() > MAX_SUBJECT_LENGTH)) {
            throw new IllegalArgumentException("A subject is between 1 and " + MAX_SUBJECT_LENGTH + " characters");
        }
    }

    public static DeliveryContent forEmail(String subject, String bodyText, String bodyHtml) {
        if (subject == null) {
            throw new IllegalArgumentException("An email needs a subject");
        }
        return new DeliveryContent(subject, bodyText, bodyHtml);
    }

    public static DeliveryContent forInApp(String message) {
        return new DeliveryContent(null, message, null);
    }
}
