package com.red_escuela.backend.billing.run.usecase;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItem;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult;
import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceRunItemResponse;
import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceRunResponse;
import com.red_escuela.backend.billing.run.dto.RunPage;
import com.red_escuela.backend.billing.run.exception.MonthlyInvoiceRunNotFoundException;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunItemRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunItemResultCount;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@UseCase
@Transactional(readOnly = true)
public class MonthlyInvoiceRunQueryService implements MonthlyInvoiceRunQueryUseCase {
    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 200;

    private final MonthlyInvoiceRunRepository runRepository;
    private final MonthlyInvoiceRunItemRepository itemRepository;

    public MonthlyInvoiceRunQueryService(MonthlyInvoiceRunRepository runRepository, MonthlyInvoiceRunItemRepository itemRepository) {
        this.runRepository = runRepository;
        this.itemRepository = itemRepository;
    }

    @Override
    public RunPage<MonthlyInvoiceRunResponse> listMonthlyInvoiceRuns(YearMonth period, Integer page, Integer size) {
        Pageable pageable = boundedPage(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<MonthlyInvoiceRun> runs = Optional.ofNullable(period)
                .map(wanted -> runRepository.findByPeriodAndMode(wanted.atDay(1), com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunMode.REAL, pageable))
                .orElseGet(() -> runRepository.findByMode(com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunMode.REAL, pageable));
        Map<Long, Map<MonthlyInvoiceRunItemResult, Long>> countsByRun = countResultsByRun(runs.getContent());
        List<MonthlyInvoiceRunResponse> items = runs.getContent().stream()
                .map(run -> MonthlyInvoiceRunResponse.of(run, countsByRun.getOrDefault(run.getId(), Map.of()))).toList();
        return new RunPage<>(items, runs.getTotalElements());
    }

    @Override
    public MonthlyInvoiceRunResponse findMonthlyInvoiceRun(Long monthlyRunId) {
        MonthlyInvoiceRun run = runRepository.findById(monthlyRunId).filter(candidate -> candidate.getMode() == com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunMode.REAL).orElseThrow(() -> new MonthlyInvoiceRunNotFoundException(monthlyRunId));
        return MonthlyInvoiceRunResponse.of(run, countResultsByRun(List.of(run)).getOrDefault(monthlyRunId, Map.of()));
    }

    @Override
    public RunPage<MonthlyInvoiceRunItemResponse> listMonthlyInvoiceRunItems(Long monthlyRunId, MonthlyInvoiceRunItemResult result, Integer page, Integer size) {
        runRepository.findById(monthlyRunId).filter(candidate -> candidate.getMode() == com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunMode.REAL).orElseThrow(() -> new MonthlyInvoiceRunNotFoundException(monthlyRunId));
        Pageable pageable = boundedPage(page, size, Sort.by(Sort.Direction.ASC, "id"));
        Page<MonthlyInvoiceRunItem> items = Optional.ofNullable(result)
                .map(wanted -> itemRepository.findByMonthlyInvoiceRunIdAndResult(monthlyRunId, wanted, pageable))
                .orElseGet(() -> itemRepository.findByMonthlyInvoiceRunId(monthlyRunId, pageable));
        return new RunPage<>(items.getContent().stream().map(MonthlyInvoiceRunItemResponse::of).toList(), items.getTotalElements());
    }

    private Map<Long, Map<MonthlyInvoiceRunItemResult, Long>> countResultsByRun(List<MonthlyInvoiceRun> runs) {
        List<MonthlyInvoiceRunItemResultCount> counts = itemRepository.countResultsByRunIds(runs.stream().map(MonthlyInvoiceRun::getId).toList());
        return counts.stream().collect(Collectors.groupingBy(MonthlyInvoiceRunItemResultCount::getRunId,
                Collectors.toMap(MonthlyInvoiceRunItemResultCount::getResult, MonthlyInvoiceRunItemResultCount::getTotal, Long::sum,
                        () -> new EnumMap<>(MonthlyInvoiceRunItemResult.class))));
    }

    private static Pageable boundedPage(Integer page, Integer size, Sort sort) {
        int boundedSize = Math.min(Math.max(Optional.ofNullable(size).orElse(DEFAULT_PAGE_SIZE), 1), MAX_PAGE_SIZE);
        int boundedPageNumber = Math.max(Optional.ofNullable(page).orElse(0), 0);
        return PageRequest.of(boundedPageNumber, boundedSize, sort);
    }
}
