package com.red_escuela.backend.enrollment.usecase.informativesession;

import com.red_escuela.backend.enrollment.dto.SaveInformativeSessionRequest;

public record UpdateInformativeSessionCommand(Long sessionId, SaveInformativeSessionRequest request) {
}
