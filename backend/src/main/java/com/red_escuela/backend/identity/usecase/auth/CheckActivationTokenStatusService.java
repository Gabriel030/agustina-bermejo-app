package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.dto.ActivationTokenStatusResponse;
import com.red_escuela.backend.identity.service.ActivationTokenStatusReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CheckActivationTokenStatusService implements CheckActivationTokenStatusUseCase {
    private final ActivationTokenStatusReader activationTokenStatusReader;

    public CheckActivationTokenStatusService(ActivationTokenStatusReader activationTokenStatusReader) {
        this.activationTokenStatusReader = activationTokenStatusReader;
    }

    @Override
    public ActivationTokenStatusResponse checkActivationTokenStatus(String token) {
        return activationTokenStatusReader.readActivationTokenStatus(token);
    }
}
