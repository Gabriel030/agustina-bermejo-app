package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;

public record ProgramYearBillingTemplateResponse(Long id, Long programId, Long termId, Integer recurringFeeInvoicesCount) {

    public static ProgramYearBillingTemplateResponse from(ProgramYearBillingTemplate template) {
        return new ProgramYearBillingTemplateResponse(
                template.getId(), template.getProgram().getId(), template.getTerm().getId(), template.getRecurringFeeInvoicesCount());
    }
}
