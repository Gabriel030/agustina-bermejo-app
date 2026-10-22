package com.red_escuela.backend.billing.operation;

public interface CohortPromotionAcquisitionPort {
    void acquireCohortPromotionWhenEligible(CohortPromotionAcquisitionRequest request);
}
