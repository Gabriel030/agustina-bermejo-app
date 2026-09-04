package com.red_escuela.backend.entity;

// This phase doesn't apply any adjustment yet, but Invoice.applyAdjustment already supports the
// full set — used starting Fase 6.2 (SURCHARGE) through Fase 6.5 (PROMOTION).
public enum InvoiceAdjustmentType {
    SCHOLARSHIP, DISCOUNT, SURCHARGE, WAIVE_DEBT, PROMOTION
}
