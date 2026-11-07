package com.red_escuela.backend.billing.simulation;

import com.red_escuela.backend.shared.time.MutableClock;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class Timeline {

    public enum Mode { DAILY, SPARSE }

    public record Job(String name, CronExpression cron, ZoneId zone, Runnable action) {

        boolean firesOncePerMonth() {
            ZonedDateTime first = cron.next(ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, zone));
            ZonedDateTime second = cron.next(first);
            return Duration.between(first, second).toDays() >= 20;
        }
    }

    public record Firing(ZonedDateTime at, Job job) {

        public String label() {
            return "[" + at.toLocalDateTime() + " " + job.name() + ".run]";
        }
    }

    public static final LocalTime ACTION_TIME = LocalTime.of(10, 0);
    private static final Set<Integer> SPARSE_DAYS_OF_MONTH = Set.of(1, 2, 6, 11, 16, 21, 28);

    private final List<Job> jobs;
    private final Mode mode;
    private final MutableClock clock;
    private final ZoneId zone;
    private final Set<LocalDate> extraDays = new HashSet<>();
    private final List<LocalDateTime[]> restartWindows = new ArrayList<>();
    private final List<String> log = new ArrayList<>();
    private Consumer<Firing> afterFiring = firing -> { };

    public Timeline(List<Job> jobs, Mode mode, MutableClock clock) {
        this.jobs = List.copyOf(jobs);
        this.mode = mode;
        this.clock = clock;
        this.zone = clock.getZone();
    }

    public static Timeline of(ApplicationContext context, Mode mode, MutableClock clock, Class<?>... jobTypes) {
        List<Job> jobs = new ArrayList<>();
        for (Class<?> type : jobTypes) {
            Object bean = context.getBean(type);
            Class<?> target = AopUtils.getTargetClass(bean);
            for (Method method : target.getDeclaredMethods()) {
                Scheduled scheduled = AnnotatedElementUtils.findMergedAnnotation(method, Scheduled.class);
                if (scheduled == null || method.getParameterCount() != 0 || scheduled.cron().isBlank()) {
                    continue;
                }
                String expression = context.getEnvironment().resolveRequiredPlaceholders(scheduled.cron());
                ZoneId jobZone = ZoneId.of(context.getEnvironment().resolveRequiredPlaceholders(scheduled.zone()));
                ReflectionUtils.makeAccessible(method);
                jobs.add(new Job(target.getSimpleName(), CronExpression.parse(expression), jobZone, () -> ReflectionUtils.invokeMethod(method, bean)));
            }
        }
        return new Timeline(jobs, mode, clock);
    }

    public Timeline onEachFiring(Consumer<Firing> listener) {
        this.afterFiring = listener;
        return this;
    }

    public Timeline alsoOn(LocalDate day) {
        extraDays.add(day);
        return this;
    }

    public Timeline restartServerBetween(LocalDateTime stoppedAt, LocalDateTime startedAt) {
        restartWindows.add(new LocalDateTime[]{stoppedAt, startedAt});
        return this;
    }

    public List<Job> jobs() {
        return jobs;
    }

    public List<String> log() {
        return List.copyOf(log);
    }

    public ZonedDateTime now() {
        return ZonedDateTime.ofInstant(clock.instant(), zone);
    }

    public List<Firing> firingsBetween(ZonedDateTime fromExclusive, ZonedDateTime toExclusive) {
        List<Firing> all = new ArrayList<>();
        for (Job job : jobs) {
            ZonedDateTime next = job.cron().next(fromExclusive.withZoneSameInstant(job.zone()));
            LocalDate lastSparseDay = null;
            while (next != null && next.isBefore(toExclusive)) {
                if (isKept(job, next, lastSparseDay)) {
                    all.add(new Firing(next.withZoneSameInstant(zone), job));
                    if (mode == Mode.SPARSE && !job.firesOncePerMonth()) {
                        lastSparseDay = next.toLocalDate();
                    }
                }
                next = job.cron().next(next);
            }
        }
        all.sort(Comparator.comparing(Firing::at).thenComparing(firing -> firing.job().name()));
        return all;
    }

    private boolean isKept(Job job, ZonedDateTime at, LocalDate lastSparseDay) {
        LocalDateTime local = at.withZoneSameInstant(zone).toLocalDateTime();
        for (LocalDateTime[] window : restartWindows) {
            if (!local.isBefore(window[0]) && local.isBefore(window[1])) {
                return false;
            }
        }
        if (mode == Mode.DAILY || job.firesOncePerMonth()) {
            return true;
        }
        LocalDate day = local.toLocalDate();
        if (day.equals(lastSparseDay)) {
            return false;
        }
        return SPARSE_DAYS_OF_MONTH.contains(day.getDayOfMonth()) || day.equals(YearMonth.from(day).atEndOfMonth()) || extraDays.contains(day);
    }

    public Timeline startAt(LocalDate day) {
        clock.setInstant(day.atTime(ACTION_TIME).atZone(zone).toInstant());
        return this;
    }

    public void advanceTo(LocalDate day) {
        ZonedDateTime target = day.atTime(ACTION_TIME).atZone(zone);
        for (Firing firing : firingsBetween(now(), target)) {
            clock.setInstant(firing.at().toInstant());
            firing.job().action().run();
            log.add(firing.label());
            afterFiring.accept(firing);
        }
        clock.setInstant(target.toInstant());
    }

    public void advanceDayByDay(LocalDate from, LocalDate to) {
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            advanceTo(day);
        }
    }

    public void advanceMonths(int months) {
        advanceTo(now().toLocalDate().plusMonths(months));
    }
}
