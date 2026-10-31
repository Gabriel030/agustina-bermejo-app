package com.red_escuela.backend.billing.run.usecase;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunMode;
import com.red_escuela.backend.billing.run.dto.DraftInvoiceGroupResponse;
import com.red_escuela.backend.billing.run.exception.MonthlyInvoiceRunNotFoundException;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import com.red_escuela.backend.billing.run.service.MonthlyRunDraftInvoiceGrouper;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class MonthlyInvoiceRunDraftQueryService implements MonthlyInvoiceRunDraftQueryUseCase {
    private final MonthlyInvoiceRunRepository runRepository;
    private final MonthlyRunDraftInvoiceGrouper draftInvoiceGrouper;

    public MonthlyInvoiceRunDraftQueryService(MonthlyInvoiceRunRepository runRepository, MonthlyRunDraftInvoiceGrouper draftInvoiceGrouper) {
        this.runRepository = runRepository;
        this.draftInvoiceGrouper = draftInvoiceGrouper;
    }

    @Override
    public List<DraftInvoiceGroupResponse> listDraftInvoiceGroups(Long monthlyRunId) {
        MonthlyInvoiceRun run = runRepository.findById(monthlyRunId).filter(candidate -> candidate.getMode() == MonthlyInvoiceRunMode.REAL)
                .orElseThrow(() -> new MonthlyInvoiceRunNotFoundException(monthlyRunId));
        return draftInvoiceGrouper.groupDraftsOfRunComparedWithPreviousPeriod(run).stream().map(DraftInvoiceGroupResponse::of).toList();
    }
}
