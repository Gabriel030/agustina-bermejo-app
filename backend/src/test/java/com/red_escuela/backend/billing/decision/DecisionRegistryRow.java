package com.red_escuela.backend.billing.decision;

public record DecisionRegistryRow(
        String id,
        String area,
        String decision,
        String status,
        String configurable,
        String implementation,
        String testCase) {

    public boolean isCurrent() {
        return status.equals("vigente") || status.equals("a confirmar");
    }

    public boolean isToConfirm() {
        return status.equals("a confirmar");
    }

    public boolean isImplementedOrPartial() {
        return implementation.equals("implementada") || implementation.startsWith("parcial (");
    }

    public boolean isViolated() {
        return implementation.startsWith("violada (");
    }

    public boolean isNotApplicable() {
        return implementation.equals("no aplica");
    }
}
