package com.red_escuela.backend.billing.collections.operation;

import java.util.Collection;
import java.util.Map;

@FunctionalInterface
public interface HeldSubjectFinder {
    String INCONSISTENT_ACCOUNT_CODE = "INCONSISTENT_ACCOUNT";
    String INCONSISTENT_ACCOUNT_MESSAGE = "La cuenta del alumno no cierra: queda fuera de este paso hasta que se revise";
    String PREVIOUS_STEP_FAILED_CODE = "PREVIOUS_STEP_FAILED";
    String PREVIOUS_STEP_FAILED_MESSAGE = "El alumno falló en un paso anterior de esta noche: queda fuera de este paso y se reintenta la noche siguiente";

    HeldSubjectFinder NONE = subjectIds -> Map.of();

    Map<Long, String> findHoldCodesAmong(Collection<Long> subjectIds);

    static String messageOf(String holdCode) {
        return PREVIOUS_STEP_FAILED_CODE.equals(holdCode) ? PREVIOUS_STEP_FAILED_MESSAGE : INCONSISTENT_ACCOUNT_MESSAGE;
    }
}
