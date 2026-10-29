package com.red_escuela.backend.billing.collections.operation;

import java.util.Collection;
import java.util.Set;

@FunctionalInterface
public interface HeldSubjectFinder {
    String INCONSISTENT_ACCOUNT_CODE = "INCONSISTENT_ACCOUNT";
    String INCONSISTENT_ACCOUNT_MESSAGE = "La cuenta del alumno no cierra: queda fuera de este paso hasta que se revise";

    HeldSubjectFinder NONE = subjectIds -> Set.of();

    Set<Long> findHeldAmong(Collection<Long> subjectIds);
}
