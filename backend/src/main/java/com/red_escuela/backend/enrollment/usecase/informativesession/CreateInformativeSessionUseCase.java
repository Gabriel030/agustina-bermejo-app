package com.red_escuela.backend.enrollment.usecase.informativesession;

import com.red_escuela.backend.enrollment.dto.InformativeSessionResponse;
import com.red_escuela.backend.enrollment.dto.SaveInformativeSessionRequest;

public interface CreateInformativeSessionUseCase {
    InformativeSessionResponse create(SaveInformativeSessionRequest request);
}
