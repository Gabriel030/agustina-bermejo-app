package com.red_escuela.backend.architecture.fixture.billing.invoice;

public class StatusAggregateFixture {

    private String status;

    public void changeStatus(String nextStatus) {
        this.status = nextStatus;
    }

    public void rogueWrite() {
        this.status = "ROGUE";
    }

    public String getStatus() {
        return status;
    }
}
