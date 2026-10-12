package com.red_escuela.backend.architecture.fixture.billing.ledger;

public class MutableEntryFixture {

    private String concept;

    public String getConcept() {
        return concept;
    }

    public void changeConcept(String newConcept) {
        this.concept = newConcept;
    }
}
