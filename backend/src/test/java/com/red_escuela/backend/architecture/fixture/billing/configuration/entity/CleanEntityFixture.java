package com.red_escuela.backend.architecture.fixture.billing.configuration.entity;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

@FixtureEntity
public class CleanEntityFixture {

    private Long id;

    private NonNegativeMoney amount;

    public void rename() {
        this.id = 1L;
    }
}
