package com.red_escuela.backend.simulation;

import com.red_escuela.backend.academicstructure.service.HolidaysMissingForNextYearReminderJob;
import com.red_escuela.backend.demo.DemoClock;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentWaitlistExpiryJob;
import com.red_escuela.backend.enrollment.service.WaitlistExpiryJob;
import com.red_escuela.backend.evaluation.specialist.ClosureConfirmationReminderJob;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigReminderJob;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.LocalDate;
import java.util.function.Consumer;

public class SimulationClockDriver {

    private final DemoClock clock;
    private final WaitlistExpiryJob waitlistExpiryJob;
    private final CourseEnrollmentWaitlistExpiryJob courseWaitlistExpiryJob;
    private final ClosureConfirmationReminderJob closureReminderJob;
    private final SectionEvaluationConfigReminderJob configReminderJob;
    private final HolidaysMissingForNextYearReminderJob holidaysReminderJob;
    private final SimulationReport report;

    public SimulationClockDriver(DemoClock clock, WaitlistExpiryJob waitlistExpiryJob,
                                 CourseEnrollmentWaitlistExpiryJob courseWaitlistExpiryJob,
                                 ClosureConfirmationReminderJob closureReminderJob,
                                 SectionEvaluationConfigReminderJob configReminderJob,
                                 HolidaysMissingForNextYearReminderJob holidaysReminderJob,
                                 SimulationReport report) {
        this.clock = clock;
        this.waitlistExpiryJob = waitlistExpiryJob;
        this.courseWaitlistExpiryJob = courseWaitlistExpiryJob;
        this.closureReminderJob = closureReminderJob;
        this.configReminderJob = configReminderJob;
        this.holidaysReminderJob = holidaysReminderJob;
        this.report = report;
    }

    public int run(LocalDate firstDay, LocalDate lastDay, Consumer<LocalDate> dayActivity) {
        int days = 0;
        for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
            clock.travelTo(day);
            ApplicationTime.useClock(clock);
            runDailyJobs();
            dayActivity.accept(day);
            days++;
        }
        return days;
    }

    public void runDailyJobs() {
        configReminderJob.sendReminders();
        courseWaitlistExpiryJob.expireWaitlists();
        waitlistExpiryJob.expireSectionWaitlists();
        waitlistExpiryJob.sendExpiryReminders();
        closureReminderJob.remindDueSections();
        holidaysReminderJob.runDaily();
        report.count("dailyJobRuns");
    }
}
