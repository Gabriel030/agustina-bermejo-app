package com.red_escuela.backend.enrollment.usecase.informativesession;

import com.red_escuela.backend.enrollment.dto.InformativeSessionResponse;

public interface CancelInformativeSessionUseCase {
    InformativeSessionResponse cancel(Long sessionId);
}
