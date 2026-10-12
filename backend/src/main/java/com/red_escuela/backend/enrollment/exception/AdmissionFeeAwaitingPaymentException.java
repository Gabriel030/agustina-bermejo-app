package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class AdmissionFeeAwaitingPaymentException extends ConflictException {
    public AdmissionFeeAwaitingPaymentException(Long studentId, Long programId) {
        super("El student " + studentId + " tiene una solicitud aprobada con la matrícula de ingreso impaga para el program " + programId);
    }
}
