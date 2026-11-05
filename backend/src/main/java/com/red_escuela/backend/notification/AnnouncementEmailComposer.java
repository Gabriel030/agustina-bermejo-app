package com.red_escuela.backend.notification;

import com.red_escuela.backend.notification.delivery.domain.DeliveryContent;

final class AnnouncementEmailComposer {

    private static final String URGENT_SUBJECT_PREFIX = "Aviso urgente: ";
    private static final String SUBJECT_PREFIX = "Aviso: ";

    private AnnouncementEmailComposer() {
    }

    static DeliveryContent compose(String title, String sectionName, boolean urgent, String kind) {
        String prefix = urgent ? URGENT_SUBJECT_PREFIX : SUBJECT_PREFIX;
        String subject = truncate(prefix + title, DeliveryContent.MAX_SUBJECT_LENGTH);
        String headline = AnnouncementEmailListener.UPDATED_KIND.equals(kind) ? "Se actualizó un aviso" : "Tenés un aviso nuevo";
        String body = headline + " en " + sectionName + ": " + title + "\n\nIngresá a la plataforma para leerlo y confirmar la lectura.";
        return DeliveryContent.forEmail(subject, body, null);
    }

    private static String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
