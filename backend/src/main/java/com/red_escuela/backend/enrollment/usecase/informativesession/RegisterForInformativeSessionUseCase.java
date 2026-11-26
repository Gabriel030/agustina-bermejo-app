package com.red_escuela.backend.enrollment.usecase.informativesession;

import com.red_escuela.backend.enrollment.dto.InformativeSessionResponse;

public interface RegisterForInformativeSessionUseCase {
    InformativeSessionResponse register(Long sessionId);
}
