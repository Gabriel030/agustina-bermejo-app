package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Promotion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PromotionResponse(
        Long id, String description, LocalDate eligibilityValidFrom, LocalDate eligibilityValidTo, String status,
        String createdByName, LocalDateTime createdAt, String closedByName, LocalDateTime closedAt,
        List<PromotionBenefitResponse> benefits) {

    public static PromotionResponse from(Promotion promotion) {
        return new PromotionResponse(
                promotion.getId(), promotion.getDescription(), promotion.getEligibilityValidFrom(), promotion.getEligibilityValidTo(),
                promotion.getStatus().name(), fullName(promotion.getCreatedBy()), promotion.getCreatedAt(),
                fullName(promotion.getClosedBy()), promotion.getClosedAt(),
                promotion.getBenefits().stream().map(PromotionBenefitResponse::from).toList());
    }

    private static String fullName(Person person) {
        return person == null ? null : person.getName() + " " + person.getLastname();
    }
}
