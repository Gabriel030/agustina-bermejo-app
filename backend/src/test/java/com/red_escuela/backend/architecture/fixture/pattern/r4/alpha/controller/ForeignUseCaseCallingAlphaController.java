package com.red_escuela.backend.architecture.fixture.pattern.r4.alpha.controller;

public class ForeignUseCaseCallingAlphaController {

    private final com.red_escuela.backend.architecture.fixture.pattern.r4.beta.usecase.BetaUseCase dependency;

    public ForeignUseCaseCallingAlphaController(com.red_escuela.backend.architecture.fixture.pattern.r4.beta.usecase.BetaUseCase dependency) {
        this.dependency = dependency;
    }
}
