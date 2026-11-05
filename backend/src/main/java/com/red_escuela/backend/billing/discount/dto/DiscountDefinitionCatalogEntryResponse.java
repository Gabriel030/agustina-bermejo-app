package com.red_escuela.backend.billing.discount.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.red_escuela.backend.billing.discount.domain.CohortPromotionStatus;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionAvailability;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionCatalogEntry;
import com.red_escuela.backend.billing.discount.domain.DiscountQuotaCostStatus;
import com.red_escuela.backend.shared.domain.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

public record DiscountDefinitionCatalogEntryResponse(@JsonUnwrapped DiscountDefinitionResponse definition,
                                                     @Schema(description = "Calculada por fecha: UPCOMING antes de availableFrom, EXPIRED después de availableUntil. Es independiente del estado CLOSED: el cliente combina ambos.") DiscountDefinitionAvailability availability,
                                                     @Schema(description = "Solo con estimatedQuotaCostStatus = ESTIMATED; en cualquier otro estado puede venir null") @Nullable Money estimatedQuotaCost,
                                                     @Schema(description = "ESTIMATED: hay costo. NO_QUOTA: la promoción no tiene cupo. NOT_APPLICABLE: no se puede calcular (sin alcance de oferta, sin cuotas o sin cálculo). NO_EFFECTIVE_PRICE: no hay precio vigente hoy para la oferta y el cargo.") DiscountQuotaCostStatus estimatedQuotaCostStatus,
                                                     @Schema(description = "Provisorio: siempre 0 hasta V2-07c1, cuando existan las promociones otorgadas que ocupan el cupo.") long quotaUsed,
                                                     @Schema(description = "Solo en promociones de cohorte; null en los demás tipos. Lo calcula el servidor con su fecha de hoy: CLOSED si se cerró; FINISHED si pasó la vigencia; UPCOMING si la vigencia o la ventana de inscripción no empezaron; QUOTA_FULL si el cupo está completo; ENROLLMENT_CLOSED si la ventana de inscripción terminó; OPEN en otro caso.") @Nullable CohortPromotionStatus cohortPromotionStatus) {

    public static DiscountDefinitionCatalogEntryResponse from(DiscountDefinitionCatalogEntry entry) {
        return new DiscountDefinitionCatalogEntryResponse(DiscountDefinitionResponse.from(entry.definition()), entry.availability(),
                entry.quotaCost().cost().map(cost -> cost.value()).orElse(null), entry.quotaCost().status(), entry.quotaUsed(),
                entry.cohortPromotionStatus().orElse(null));
    }
}
