package com.red_escuela.backend.billing.planning.policy;

import java.util.Optional;

public interface InstallmentCapContext {
    long installmentsIssued();

    Optional<Integer> installmentCap();
}
