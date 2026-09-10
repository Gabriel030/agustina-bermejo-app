package com.red_escuela.backend.billing.usecase.promotion;

import com.red_escuela.backend.billing.entity.Promotion;

import java.util.List;

public interface FindActivePromotionsUseCase {
    List<Promotion> findActive();
}
