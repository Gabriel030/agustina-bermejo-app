package com.red_escuela.backend.billing.exception;

// Encontrado en auditoría (2026-09-03): PaymentPlanSelectionService resolvía la opción por id
// global sin cruzarla contra el curso/carrera del enrollment — nada impedía elegir la opción de
// un curso o carrera ajena.
public class PaymentPlanOptionMismatchException extends RuntimeException {
    public PaymentPlanOptionMismatchException(Long paymentPlanOptionId) {
        super("Payment plan option " + paymentPlanOptionId + " does not belong to this course/program");
    }
}
