package com.red_escuela.backend.graduation.usecase.request;

public record RejectGraduationRequestCommand(Long graduationRequestId, String motivo) {
}
