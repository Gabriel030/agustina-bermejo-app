package com.red_escuela.backend.billing.run.usecase;

import com.red_escuela.backend.billing.run.domain.BillingRun;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.domain.NightlyBillingRun;
import com.red_escuela.backend.billing.run.dto.BillingRunItemResponse;
import com.red_escuela.backend.billing.run.dto.NightlyBillingRunResponse;
import com.red_escuela.backend.billing.run.exception.BillingRunStepNotFoundException;
import com.red_escuela.backend.billing.run.exception.InvalidBillingRunRangeException;
import com.red_escuela.backend.billing.run.exception.NightlyBillingRunNotFoundException;
import com.red_escuela.backend.billing.run.repository.BillingRunItemRepository;
import com.red_escuela.backend.billing.run.repository.BillingRunRepository;
import com.red_escuela.backend.billing.run.repository.NightlyBillingRunRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@UseCase
@Transactional(readOnly = true)
public class BillingRunQueryService implements BillingRunQueryUseCase {
    private static final int DEFAULT_RANGE_DAYS = 30;

    private final NightlyBillingRunRepository nightlyRunRepository;
    private final BillingRunRepository stepRunRepository;
    private final BillingRunItemRepository itemRepository;
    private final Clock clock;

    public BillingRunQueryService(NightlyBillingRunRepository nightlyRunRepository, BillingRunRepository stepRunRepository, BillingRunItemRepository itemRepository,
                                  Clock clock) {
        this.nightlyRunRepository = nightlyRunRepository;
        this.stepRunRepository = stepRunRepository;
        this.itemRepository = itemRepository;
        this.clock = clock;
    }

    @Override
    public List<NightlyBillingRunResponse> listNightlyBillingRuns(LocalDate from, LocalDate to) {
        LocalDate upperBound = to != null ? to : LocalDate.now(clock);
        LocalDate lowerBound = from != null ? from : upperBound.minusDays(DEFAULT_RANGE_DAYS);
        if (lowerBound.isAfter(upperBound)) {
            throw new InvalidBillingRunRangeException();
        }
        List<NightlyBillingRun> runs = nightlyRunRepository.findByBusinessDateBetweenNewestFirst(lowerBound, upperBound);
        Map<Long, List<BillingRun>> stepsByRun = stepRunRepository.findByNightlyBillingRunIdInOrderByIdAsc(runs.stream().map(NightlyBillingRun::getId).toList()).stream()
                .collect(Collectors.groupingBy(BillingRun::getNightlyBillingRunId, Collectors.toList()));
        return runs.stream().map(run -> NightlyBillingRunResponse.of(run, stepsByRun.getOrDefault(run.getId(), List.of()))).toList();
    }

    @Override
    public NightlyBillingRunResponse findNightlyBillingRun(Long nightlyRunId) {
        NightlyBillingRun run = nightlyRunRepository.findById(nightlyRunId).orElseThrow(() -> new NightlyBillingRunNotFoundException(nightlyRunId));
        return NightlyBillingRunResponse.of(run, stepRunRepository.findByNightlyBillingRunIdOrderByIdAsc(nightlyRunId));
    }

    @Override
    public List<BillingRunItemResponse> listBillingRunItems(Long nightlyRunId, BillingRunStep step) {
        nightlyRunRepository.findById(nightlyRunId).orElseThrow(() -> new NightlyBillingRunNotFoundException(nightlyRunId));
        BillingRun stepRun = stepRunRepository.findByNightlyBillingRunIdAndStep(nightlyRunId, step)
                .orElseThrow(() -> new BillingRunStepNotFoundException(nightlyRunId, step.name()));
        return itemRepository.findByBillingRunIdOrderByIdAsc(stepRun.getId()).stream().map(BillingRunItemResponse::of).toList();
    }
}
