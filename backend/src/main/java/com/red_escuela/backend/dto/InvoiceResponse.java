package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.Invoice;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceResponse(
        Long id, Long studentId, String concept, BigDecimal amount, LocalDate dueDate,
        String status, BigDecimal saldoPendiente) {

    public static InvoiceResponse from(Invoice invoice, BigDecimal saldoPendiente) {
        return new InvoiceResponse(
                invoice.getId(), invoice.getStudent().getId(), invoice.getConcept(), invoice.getAmount(),
                invoice.getDueDate(), invoice.getStatus().name(), saldoPendiente);
    }
}
