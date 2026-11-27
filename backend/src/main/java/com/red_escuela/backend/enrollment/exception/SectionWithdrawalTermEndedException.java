package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class SectionWithdrawalTermEndedException extends ConflictException implements ReportsErrorCode {

    public SectionWithdrawalTermEndedException() {
        super("El término ya terminó: para darte de baja hablá con Administración");
    }
}
