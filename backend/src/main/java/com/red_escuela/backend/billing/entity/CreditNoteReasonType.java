package com.red_escuela.backend.billing.entity;

// ADMINISTRATIVE_CORRECTION/OTHER: solo vía el endpoint manual (CreateCreditNoteRequest las
// valida). EARLY_WITHDRAWAL_UPFRONT_UNUSED/RETROACTIVE_SCHOLARSHIP: solo las genera el sistema
// (EnrollmentWithdrawalCreditService/ScholarshipService) — un CreateCreditNoteRequest con alguna
// de estas dos se rechaza (InvalidCreditNoteReasonException).
public enum CreditNoteReasonType {
    ADMINISTRATIVE_CORRECTION, OTHER, EARLY_WITHDRAWAL_UPFRONT_UNUSED, RETROACTIVE_SCHOLARSHIP
}
