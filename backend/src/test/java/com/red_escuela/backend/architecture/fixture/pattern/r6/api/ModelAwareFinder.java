package com.red_escuela.backend.architecture.fixture.pattern.r6.api;

import com.red_escuela.backend.architecture.fixture.pattern.r6.entity.ExposedEntity;

public class ModelAwareFinder {

    public ExposedEntity find() {
        return new ExposedEntity();
    }
}
