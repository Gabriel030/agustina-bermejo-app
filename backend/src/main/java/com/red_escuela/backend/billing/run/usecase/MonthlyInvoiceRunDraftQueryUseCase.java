package com.red_escuela.backend.billing.run.usecase;

import com.red_escuela.backend.billing.run.dto.DraftInvoiceGroupResponse;

import java.util.List;

public interface MonthlyInvoiceRunDraftQueryUseCase {
    List<DraftInvoiceGroupResponse> listDraftInvoiceGroups(Long monthlyRunId);
}
