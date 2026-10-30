package com.red_escuela.backend.architecture.fixture.pattern.r2.usecase;

public class ChainingFlowService {

    private final OtherFlowUseCase other;

    public ChainingFlowService(OtherFlowUseCase other) {
        this.other = other;
    }
}
