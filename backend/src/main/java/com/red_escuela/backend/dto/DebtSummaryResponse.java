package com.red_escuela.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record DebtSummaryResponse(boolean tieneDeuda, BigDecimal montoAdeudado, List<InvoiceResponse> facturasPendientes) {
}
