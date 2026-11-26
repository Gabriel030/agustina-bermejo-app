package com.red_escuela.backend.enrollment.api;

public interface ProgramAdmissionFeePort {

    void attachAdmissionFee(Long programEnrollmentId, Long admissionInvoiceId);
}
