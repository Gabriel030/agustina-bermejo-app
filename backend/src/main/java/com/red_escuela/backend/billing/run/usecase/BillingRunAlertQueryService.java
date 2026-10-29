package com.red_escuela.backend.billing.run.usecase;

import com.red_escuela.backend.billing.run.domain.NightlyBillingRun;
import com.red_escuela.backend.billing.run.domain.NightlyBillingSchedule;
import com.red_escuela.backend.billing.run.domain.NightlyBillingRunStatus;
import com.red_escuela.backend.billing.run.dto.BillingRunAlertResponse;
import com.red_escuela.backend.billing.run.dto.BillingRunAlertType;
import com.red_escuela.backend.billing.run.exception.InvalidBillingRunRangeException;
import com.red_escuela.backend.billing.run.repository.NightlyBillingRunRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@UseCase
@Transactional(readOnly = true)
public class BillingRunAlertQueryService implements BillingRunAlertQueryUseCase {
    private static final int DEFAULT_RANGE_DAYS = 30;
    private static final Map<NightlyBillingRunStatus, BillingRunAlertType> ALERT_TYPE_BY_STATUS = Map.of(
            NightlyBillingRunStatus.FAILED, BillingRunAlertType.NIGHTLY_RUN_FAILED,
            NightlyBillingRunStatus.COMPLETED_WITH_ERRORS, BillingRunAlertType.NIGHTLY_RUN_COMPLETED_WITH_ERRORS);
    private static final Map<BillingRunAlertType, String> MESSAGE_BY_TYPE = Map.of(
            BillingRunAlertType.NIGHTLY_RUN_FAILED, "El proceso nocturno falló y se detuvo",
            BillingRunAlertType.NIGHTLY_RUN_COMPLETED_WITH_ERRORS, "El proceso nocturno terminó con errores",
            BillingRunAlertType.NIGHTLY_RUN_MISSED, "El proceso nocturno no corrió");
    private static final CronExpression WATCHDOG_CRON = CronExpression.parse(NightlyBillingSchedule.WATCHDOG_CRON);

    private final NightlyBillingRunRepository nightlyRunRepository;
    private final Clock clock;

    public BillingRunAlertQueryService(NightlyBillingRunRepository nightlyRunRepository, Clock clock) {
        this.nightlyRunRepository = nightlyRunRepository;
        this.clock = clock;
    }

    @Override
    public List<BillingRunAlertResponse> listBillingRunAlerts(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        LocalDate upperBound = Optional.ofNullable(to).orElse(today);
        LocalDate lowerBound = Optional.ofNullable(from).orElse(upperBound.minusDays(DEFAULT_RANGE_DAYS));
        rejectInvertedRange(lowerBound, upperBound);
        List<NightlyBillingRun> runs = nightlyRunRepository.findByBusinessDateBetweenNewestFirst(lowerBound, upperBound);
        Stream<BillingRunAlertResponse> alertsOfRuns = runs.stream().filter(run -> ALERT_TYPE_BY_STATUS.containsKey(run.getStatus())).map(this::alertOfRun);
        Stream<BillingRunAlertResponse> alertsOfMissedNights = findMissedBusinessDates(runs, lowerBound, upperBound, today).stream()
                .map(date -> alertOf(BillingRunAlertType.NIGHTLY_RUN_MISSED, date, null));
        return Stream.concat(alertsOfRuns, alertsOfMissedNights).sorted(Comparator.comparing(BillingRunAlertResponse::businessDate).reversed()).toList();
    }

    private static void rejectInvertedRange(LocalDate lowerBound, LocalDate upperBound) {
        Optional.of(lowerBound).filter(lower -> lower.isAfter(upperBound)).ifPresent(lower -> {
            throw new InvalidBillingRunRangeException();
        });
    }

    private BillingRunAlertResponse alertOfRun(NightlyBillingRun run) {
        return alertOf(ALERT_TYPE_BY_STATUS.get(run.getStatus()), run.getBusinessDate(), run.getId());
    }

    private static BillingRunAlertResponse alertOf(BillingRunAlertType type, LocalDate businessDate, Long nightlyRunId) {
        return new BillingRunAlertResponse(type, businessDate, nightlyRunId, MESSAGE_BY_TYPE.get(type));
    }

    private List<LocalDate> findMissedBusinessDates(List<NightlyBillingRun> runsInRange, LocalDate lowerBound, LocalDate upperBound, LocalDate today) {
        Map<LocalDate, NightlyBillingRun> runByDate = runsInRange.stream().collect(Collectors.toMap(NightlyBillingRun::getBusinessDate, Function.identity()));
        LocalDate firstDayWithHistory = nightlyRunRepository.findFirstByOrderByBusinessDateAsc().map(NightlyBillingRun::getBusinessDate).orElse(today.plusDays(1));
        LocalDate firstCandidate = lowerBound.isAfter(firstDayWithHistory) ? lowerBound : firstDayWithHistory;
        LocalDateTime now = LocalDateTime.now(clock);
        return firstCandidate.datesUntil(upperBound.plusDays(1))
                .filter(date -> !runByDate.containsKey(date))
                .filter(date -> watchdogHasAlreadyLookedAt(date, now))
                .toList();
    }

    private static boolean watchdogHasAlreadyLookedAt(LocalDate businessDate, LocalDateTime now) {
        LocalDateTime watchdogMoment = WATCHDOG_CRON.next(businessDate.atStartOfDay().minusSeconds(1));
        return !now.isBefore(watchdogMoment);
    }
}
