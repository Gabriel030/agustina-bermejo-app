package com.red_escuela.backend.billing.run.usecase;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItem;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult;
import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceRunItemResponse;
import com.red_escuela.backend.billing.run.exception.MonthlyInvoiceRunNotFoundException;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunItemRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@UseCase
@Transactional(readOnly = true)
public class MonthlyInvoiceRunQueryService implements MonthlyInvoiceRunQueryUseCase {
    private final MonthlyInvoiceRunRepository runRepository;
    private final MonthlyInvoiceRunItemRepository itemRepository;

    public MonthlyInvoiceRunQueryService(MonthlyInvoiceRunRepository runRepository, MonthlyInvoiceRunItemRepository itemRepository) {
        this.runRepository = runRepository;
        this.itemRepository = itemRepository;
    }

    @Override
    public List<MonthlyInvoiceRunItemResponse> listMonthlyInvoiceRunItems(Long monthlyRunId, MonthlyInvoiceRunItemResult result) {
        runRepository.findById(monthlyRunId).orElseThrow(() -> new MonthlyInvoiceRunNotFoundException(monthlyRunId));
        List<MonthlyInvoiceRunItem> items = Optional.ofNullable(result)
                .map(wanted -> itemRepository.findByMonthlyInvoiceRunIdAndResultOrderByIdAsc(monthlyRunId, wanted))
                .orElseGet(() -> itemRepository.findByMonthlyInvoiceRunIdOrderByIdAsc(monthlyRunId));
        return items.stream().map(MonthlyInvoiceRunItemResponse::of).toList();
    }
}
