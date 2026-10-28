package com.red_escuela.backend.billing.discount.dto;

import java.util.List;

public record LostPromotionBenefitsSlice(List<LostPromotionBenefitResponse> items, long totalElements) {
}
