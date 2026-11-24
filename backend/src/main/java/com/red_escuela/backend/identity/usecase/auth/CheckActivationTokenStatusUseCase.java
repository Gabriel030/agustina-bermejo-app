package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.dto.ActivationTokenStatusResponse;

public interface CheckActivationTokenStatusUseCase {
    ActivationTokenStatusResponse checkActivationTokenStatus(String token);
}
