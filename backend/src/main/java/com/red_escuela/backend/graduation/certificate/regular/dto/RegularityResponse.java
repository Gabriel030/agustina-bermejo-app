package com.red_escuela.backend.graduation.certificate.regular.dto;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record RegularityResponse(RegularityKind kind, Long enrollmentId,
                                 @Deprecated @Schema(deprecated = true, description = "Usar institutionallyRegular")
                                 boolean regular,
                                 List<RegularityReason> reasons,
                                 boolean attendanceControlled, String billingMode,
                                 @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) boolean institutionallyRegular,
                                 @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) PaymentStanding paymentStanding) {
}
