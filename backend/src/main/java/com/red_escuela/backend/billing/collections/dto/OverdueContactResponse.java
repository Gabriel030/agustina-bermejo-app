package com.red_escuela.backend.billing.collections.dto;

import com.red_escuela.backend.billing.collections.domain.OverdueContactReason;
import com.red_escuela.backend.shared.domain.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.util.List;

public record OverdueContactResponse(Long studentId, String studentName, String dni, int oldestDaysOverdue, int overdueInstallments, List<Money> overdueAmounts,
                                     @Schema(description = "Opcional; null si nunca se le avisó") @Nullable LocalDate lastReminderDate,
                                     @Schema(description = "true si ayer todavía no estaba en la lista") boolean newlyListed,
                                     @Schema(description = "Qué parte del criterio cumple hoy") OverdueContactReason entryReason) {
}
