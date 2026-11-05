package com.red_escuela.backend.architecture.fixture.pattern.r8.api;

import com.red_escuela.backend.architecture.fixture.pattern.r8.repository.WidgetRepository;

public class RepositoryLeakyFinder {

    public WidgetRepository repository() {
        return null;
    }

    public void use(WidgetRepository repository) {
    }
}
