package com.red_escuela.backend.billing.simulation;

import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TimelineTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Runnable NOTHING = () -> { };

    private static Timeline.Job job(String name, String cron) {
        return new Timeline.Job(name, CronExpression.parse(cron), ZONE, NOTHING);
    }

    private static Timeline timeline(Timeline.Mode mode, Timeline.Job... jobs) {
        return new Timeline(List.of(jobs), mode, MutableClock.INSTANCE);
    }

    private static ZonedDateTime at(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(ZONE);
    }

    @Test
    @Tag("D-449")
    void changingTheCronOfAJobChangesItsSignature() {
        Timeline.Job nightly = job("NightlyBillingJob", "0 0 3 * * *");
        Timeline.Job moved = job("NightlyBillingJob", "0 30 2 * * *");

        List<Timeline.Firing> original = timeline(Timeline.Mode.DAILY, nightly).firingsBetween(at(2027, 3, 1, 0, 0), at(2027, 3, 3, 0, 0));
        List<Timeline.Firing> changed = timeline(Timeline.Mode.DAILY, moved).firingsBetween(at(2027, 3, 1, 0, 0), at(2027, 3, 3, 0, 0));

        assertThat(original).extracting(Timeline.Firing::at).containsExactly(at(2027, 3, 1, 3, 0), at(2027, 3, 2, 3, 0));
        assertThat(changed).extracting(Timeline.Firing::at).containsExactly(at(2027, 3, 1, 2, 30), at(2027, 3, 2, 2, 30));
    }

    @Test
    @Tag("D-449")
    void timelineOrdersTiesByHourThenName() {
        Timeline.Job zeta = job("ZetaJob", "0 0 4 * * *");
        Timeline.Job alpha = job("AlphaJob", "0 0 4 * * *");
        Timeline.Job earlier = job("MiddleJob", "0 0 3 * * *");

        List<Timeline.Firing> firings = timeline(Timeline.Mode.DAILY, zeta, alpha, earlier).firingsBetween(at(2027, 3, 1, 0, 0), at(2027, 3, 2, 0, 0));

        assertThat(firings).extracting(firing -> firing.job().name()).containsExactly("MiddleJob", "AlphaJob", "ZetaJob");
    }

    @Test
    @Tag("D-055")
    void timelineSkipsNoSignatureBetweenRestartBounds() {
        Timeline.Job nightly = job("NightlyBillingJob", "0 0 3 * * *");
        Timeline timeline = timeline(Timeline.Mode.DAILY, nightly)
                .restartServerBetween(LocalDateTime.of(2027, 3, 2, 0, 0), LocalDateTime.of(2027, 3, 4, 12, 0));

        List<Timeline.Firing> firings = timeline.firingsBetween(at(2027, 3, 1, 0, 0), at(2027, 3, 6, 0, 0));

        assertThat(firings).extracting(Timeline.Firing::at).containsExactly(at(2027, 3, 1, 3, 0), at(2027, 3, 5, 3, 0));
    }

    @Test
    void sparseModeRunsMonthlyJobEveryTimeAndDailyJobOnListedDays() {
        Timeline.Job nightly = job("NightlyBillingJob", "0 0 3 * * *");
        Timeline.Job monthly = job("MonthlyInvoiceIssuanceJob", "0 0 4 1 * *");
        Timeline.Job retry = job("FailedInvoiceRetryJob", "0 0/30 6-21 * * *");

        List<Timeline.Firing> firings = timeline(Timeline.Mode.SPARSE, nightly, monthly, retry).alsoOn(LocalDate.of(2027, 4, 14))
                .firingsBetween(at(2027, 2, 28, 22, 0), at(2027, 5, 1, 0, 0));

        List<LocalDate> nightlyDays = firings.stream().filter(firing -> firing.job() == nightly).map(firing -> firing.at().toLocalDate()).toList();
        assertThat(nightlyDays).containsExactly(
                LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 2), LocalDate.of(2027, 3, 6), LocalDate.of(2027, 3, 11), LocalDate.of(2027, 3, 16),
                LocalDate.of(2027, 3, 21), LocalDate.of(2027, 3, 28), LocalDate.of(2027, 3, 31),
                LocalDate.of(2027, 4, 1), LocalDate.of(2027, 4, 2), LocalDate.of(2027, 4, 6), LocalDate.of(2027, 4, 11), LocalDate.of(2027, 4, 14),
                LocalDate.of(2027, 4, 16), LocalDate.of(2027, 4, 21), LocalDate.of(2027, 4, 28), LocalDate.of(2027, 4, 30));
        assertThat(firings.stream().filter(firing -> firing.job() == monthly).map(Timeline.Firing::at).toList())
                .containsExactly(at(2027, 3, 1, 4, 0), at(2027, 4, 1, 4, 0));
        assertThat(firings.stream().filter(firing -> firing.job() == retry).map(Timeline.Firing::at).toList())
                .hasSameSizeAs(nightlyDays).allSatisfy(firing -> assertThat(firing.toLocalTime().getHour()).isEqualTo(6));
        assertThat(firings.subList(0, 3)).extracting(Timeline.Firing::label).containsExactly(
                "[2027-03-01T03:00 NightlyBillingJob.run]", "[2027-03-01T04:00 MonthlyInvoiceIssuanceJob.run]", "[2027-03-01T06:00 FailedInvoiceRetryJob.run]");
    }

    @Test
    void dailyModeKeepsEverySignatureOfTheRetryJob() {
        Timeline.Job retry = job("FailedInvoiceRetryJob", "0 0/30 6-21 * * *");

        List<Timeline.Firing> firings = timeline(Timeline.Mode.DAILY, retry).firingsBetween(at(2027, 3, 1, 0, 0), at(2027, 3, 2, 0, 0));

        assertThat(firings).hasSize(32);
    }
}
