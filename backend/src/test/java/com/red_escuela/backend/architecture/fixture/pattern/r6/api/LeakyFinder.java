package com.red_escuela.backend.architecture.fixture.pattern.r6.api;

import com.red_escuela.backend.architecture.fixture.pattern.r6.entity.HiddenEntity;

import java.util.List;

public class LeakyFinder {

    public List<HiddenEntity> findAll() {
        return List.of();
    }

    public void rename(HiddenEntity entity) {
    }
}
