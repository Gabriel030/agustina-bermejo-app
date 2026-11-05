package com.red_escuela.backend.billing.collections.domain;

public enum EnrollmentPaymentEvent {
    STANDING_OPENED("Se abrió el estado de cobranza de la inscripción"),
    OVERDUE_DEBT_DETECTED("La inscripción tiene facturas vencidas"),
    OVERDUE_DEBT_CLEARED("La inscripción ya no tiene facturas vencidas"),
    MONTH_CLOSED_WITH_UNPAID_INSTALLMENT("Cerró el mes con cuotas mensuales vencidas e impagas"),
    ACCOUNT_SETTLED("Se pagó todo lo vencido de la inscripción, recargos incluidos"),
    TERM_CLOSED_WHILE_SUSPENDED("Cerró el período lectivo con la inscripción suspendida"),
    COURSE_ENDED_WHILE_SUSPENDED("Terminó el curso con la inscripción suspendida");

    private final String reason;

    EnrollmentPaymentEvent(String reason) {
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
