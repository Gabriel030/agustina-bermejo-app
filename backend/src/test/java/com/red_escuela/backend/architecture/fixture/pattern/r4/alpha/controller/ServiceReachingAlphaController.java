package com.red_escuela.backend.architecture.fixture.pattern.r4.alpha.controller;

public class ServiceReachingAlphaController {

    private final com.red_escuela.backend.architecture.fixture.pattern.r4.beta.service.BetaSpecialist dependency;

    public ServiceReachingAlphaController(com.red_escuela.backend.architecture.fixture.pattern.r4.beta.service.BetaSpecialist dependency) {
        this.dependency = dependency;
    }
}
