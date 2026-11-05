package com.red_escuela.backend.architecture.fixture.pattern.r8.api;

import com.red_escuela.backend.architecture.fixture.pattern.r8.repository.WidgetRepository;

public class InternalRepositoryFinder {
    private final WidgetRepository widgetRepository;

    public InternalRepositoryFinder(WidgetRepository widgetRepository) {
        this.widgetRepository = widgetRepository;
    }

    public WidgetSummary summary() {
        return new WidgetSummary(widgetRepository.toString());
    }

    public record WidgetSummary(String name) {
    }
}
