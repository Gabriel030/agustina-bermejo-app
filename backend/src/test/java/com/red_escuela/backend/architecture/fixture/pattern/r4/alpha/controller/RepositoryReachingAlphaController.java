package com.red_escuela.backend.architecture.fixture.pattern.r4.alpha.controller;

public class RepositoryReachingAlphaController {

    private final com.red_escuela.backend.architecture.fixture.pattern.r4.alpha.repository.AlphaRepository dependency;

    public RepositoryReachingAlphaController(com.red_escuela.backend.architecture.fixture.pattern.r4.alpha.repository.AlphaRepository dependency) {
        this.dependency = dependency;
    }
}
