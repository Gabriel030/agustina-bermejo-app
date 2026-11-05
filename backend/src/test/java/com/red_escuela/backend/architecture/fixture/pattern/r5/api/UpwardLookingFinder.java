package com.red_escuela.backend.architecture.fixture.pattern.r5.api;

public class UpwardLookingFinder {

    private final com.red_escuela.backend.architecture.fixture.pattern.r5.usecase.PricingUseCase useCase;
    private final com.red_escuela.backend.architecture.fixture.pattern.r5.controller.PricingController controller;

    public UpwardLookingFinder(com.red_escuela.backend.architecture.fixture.pattern.r5.usecase.PricingUseCase useCase,
            com.red_escuela.backend.architecture.fixture.pattern.r5.controller.PricingController controller) {
        this.useCase = useCase;
        this.controller = controller;
    }
}
