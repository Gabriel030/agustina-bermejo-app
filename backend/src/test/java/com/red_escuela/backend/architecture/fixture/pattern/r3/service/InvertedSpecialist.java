package com.red_escuela.backend.architecture.fixture.pattern.r3.service;

public class InvertedSpecialist {

    private final com.red_escuela.backend.architecture.fixture.pattern.r3.usecase.SomeUseCase useCase;

    public InvertedSpecialist(com.red_escuela.backend.architecture.fixture.pattern.r3.usecase.SomeUseCase useCase) {
        this.useCase = useCase;
    }
}
