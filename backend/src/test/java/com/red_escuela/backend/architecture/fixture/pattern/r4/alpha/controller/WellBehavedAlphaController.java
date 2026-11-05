package com.red_escuela.backend.architecture.fixture.pattern.r4.alpha.controller;

public class WellBehavedAlphaController {

    private final com.red_escuela.backend.architecture.fixture.pattern.r4.alpha.usecase.AlphaUseCase useCase;
    private final com.red_escuela.backend.architecture.fixture.pattern.r4.alpha.service.SharedCollaborator collaborator;

    public WellBehavedAlphaController(com.red_escuela.backend.architecture.fixture.pattern.r4.alpha.usecase.AlphaUseCase useCase,
            com.red_escuela.backend.architecture.fixture.pattern.r4.alpha.service.SharedCollaborator collaborator) {
        this.useCase = useCase;
        this.collaborator = collaborator;
    }
}
