package com.red_escuela.backend.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.red_escuela.backend.evaluation.api.PaymentsStanding;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ClosureSheetAdministrativeResponse(
        PaymentsStanding payments,
        boolean documentsComplete,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) List<String> missingDocuments) {
}
