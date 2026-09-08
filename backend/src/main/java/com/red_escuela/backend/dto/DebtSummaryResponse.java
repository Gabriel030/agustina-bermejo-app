package com.red_escuela.backend.dto;

import java.math.BigDecimal;
import java.util.List;
import com.red_escuela.backend.billing.dto.InvoiceResponse;

public record DebtSummaryResponse(boolean tieneDeuda, BigDecimal montoAdeudado, List<InvoiceResponse> facturasPendientes) {
}
