package com.red_escuela.backend.graduation.usecase.graduationrequest;

public record RejectGraduationRequestCommand(Long graduationRequestId, String motivo) {
}
