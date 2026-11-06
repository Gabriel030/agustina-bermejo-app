package com.red_escuela.backend.attendance.dto;

import com.red_escuela.backend.attendance.exception.InvalidJustificationInputException;
import jakarta.validation.constraints.NotBlank;

import java.util.regex.Pattern;

public record JustificarAsistenciaRequest(@NotBlank String reason, String attachmentUrl, String exceptionReason) {

    private static final int REASON_MAX_LENGTH = 500;
    private static final int ATTACHMENT_MAX_LENGTH = 255;
    private static final Pattern STORED_PROOF_KEY = Pattern.compile("students/\\d+/[A-Za-z0-9_-]+\\.(pdf|jpg|png)");

    public void requireValidLimits() {
        if (reason.length() > REASON_MAX_LENGTH) {
            throw new InvalidJustificationInputException("El motivo no puede superar los " + REASON_MAX_LENGTH + " caracteres");
        }
        if (exceptionReason != null && exceptionReason.length() > REASON_MAX_LENGTH) {
            throw new InvalidJustificationInputException(
                    "El motivo de la excepción no puede superar los " + REASON_MAX_LENGTH + " caracteres");
        }
        if (attachmentUrl != null
                && (attachmentUrl.length() > ATTACHMENT_MAX_LENGTH || !STORED_PROOF_KEY.matcher(attachmentUrl).matches())) {
            throw new InvalidJustificationInputException("El adjunto debe ser un comprobante almacenado");
        }
    }
}
