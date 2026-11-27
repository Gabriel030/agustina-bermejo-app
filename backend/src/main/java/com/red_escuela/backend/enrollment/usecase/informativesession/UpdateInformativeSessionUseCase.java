package com.red_escuela.backend.enrollment.usecase.informativesession;

import com.red_escuela.backend.enrollment.dto.InformativeSessionResponse;

public interface UpdateInformativeSessionUseCase {
    InformativeSessionResponse update(UpdateInformativeSessionCommand command);
}
