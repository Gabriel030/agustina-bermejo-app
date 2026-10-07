package com.red_escuela.backend.billing.invariant;

public enum KnownInvariantViolation {
    UNDATED_TERMS_BILL_JANUARY_AND_FEBRUARY(
            "I-15",
            "D-001",
            "V2-10",
            "MonthlyInvoicePlannerCorrectionsTest#secondYearStudentOfUndatedTermsIsStillBilledAndTheCapCountsOnlyThatYear",
            "A school year whose Term has no dates emits installments in January");

    private final String invariant;
    private final String decision;
    private final String fixedByPhase;
    private final String test;
    private final String reason;

    KnownInvariantViolation(String invariant, String decision, String fixedByPhase, String test, String reason) {
        this.invariant = invariant;
        this.decision = decision;
        this.fixedByPhase = fixedByPhase;
        this.test = test;
        this.reason = reason;
    }

    public String invariant() {
        return invariant;
    }

    public String decision() {
        return decision;
    }

    public String fixedByPhase() {
        return fixedByPhase;
    }

    public String test() {
        return test;
    }

    public String reason() {
        return reason;
    }
}
