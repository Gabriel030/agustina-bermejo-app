package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorDetails;

import java.util.Map;

public class PendingDebtException extends ConflictException implements ReportsErrorDetails {
    public static final String TOTAL_DUE_KEY = "totalDue";
    public static final String INSTALLMENT_COUNT_KEY = "installmentCount";

    private final Money totalDue;
    private final int installmentCount;

    public PendingDebtException(Long studentId, Money totalDue, int installmentCount) {
        super("El alumno " + studentId + " tiene facturas pendientes, no se puede verificar el egreso");
        this.totalDue = totalDue;
        this.installmentCount = installmentCount;
    }

    @Override
    public Map<String, Object> details() {
        return Map.of(TOTAL_DUE_KEY, totalDue.amount(), INSTALLMENT_COUNT_KEY, installmentCount);
    }
}
