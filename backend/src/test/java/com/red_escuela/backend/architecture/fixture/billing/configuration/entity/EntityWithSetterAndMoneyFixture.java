package com.red_escuela.backend.architecture.fixture.billing.configuration.entity;

import com.red_escuela.backend.shared.domain.Money;

@FixtureEntity
public class EntityWithSetterAndMoneyFixture {

    private Long id;

    private Money amount;

    public void setId(Long id) {
        this.id = id;
    }

    public Money getAmount() {
        return amount;
    }
}
