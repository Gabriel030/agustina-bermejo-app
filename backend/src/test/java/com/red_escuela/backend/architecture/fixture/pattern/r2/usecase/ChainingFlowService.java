package com.red_escuela.backend.architecture.fixture.pattern.r2.usecase;

import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ChainingFlowService {

    private final OtherFlowUseCase other;

    public ChainingFlowService(OtherFlowUseCase other) {
        this.other = other;
    }
}
