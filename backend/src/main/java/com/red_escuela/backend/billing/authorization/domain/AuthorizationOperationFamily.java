package com.red_escuela.backend.billing.authorization.domain;

public enum AuthorizationOperationFamily {
    REFUNDS("Reembolsos"),
    INVOICES_AND_NOTES("Facturas y notas"),
    PAYMENTS_AND_CHARGEBACKS("Pagos y contracargos"),
    DISCOUNTS_AND_PRICES("Descuentos y precios"),
    CONFIGURATION("Configuración");

    private final String label;

    AuthorizationOperationFamily(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
