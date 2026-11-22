package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.graduation.certificate.dto.RecipientType;
import com.red_escuela.backend.shared.exception.RequestFieldRequiredException;
import org.springframework.stereotype.Component;

@Component
public class RecipientLabelFormatter {

    private static final String PREFIX = "Se extiende a pedido del interesado para ser presentado ante: ";

    public String labelOf(RecipientType type, String text) {
        String name = text == null ? "" : text.trim();
        if (type == null) {
            if (name.isEmpty()) {
                return null;
            }
            throw new RequestFieldRequiredException("Indicá ante quién se presenta la constancia");
        }
        if (name.isEmpty()) {
            throw new RequestFieldRequiredException("Indicá el nombre de quien recibe la constancia");
        }
        return type == RecipientType.OTHER ? PREFIX + name : PREFIX + typeLabel(type) + ", " + name;
    }

    private String typeLabel(RecipientType type) {
        return switch (type) {
            case HEALTH_INSURANCE -> "Obra social";
            case EMPLOYER -> "Empleador";
            case STUDENT_TRANSPORT -> "Transporte estudiantil";
            case OTHER -> "Otro";
        };
    }
}
