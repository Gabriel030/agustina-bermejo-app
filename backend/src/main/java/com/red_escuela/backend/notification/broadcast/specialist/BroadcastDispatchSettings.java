package com.red_escuela.backend.notification.broadcast.specialist;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastDailyQuota;
import com.red_escuela.backend.notification.delivery.domain.EmailDispatchWindow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

@Component
public class BroadcastDispatchSettings {

    private final BroadcastDailyQuota quota;
    private final int maxPerRun;
    private final EmailDispatchWindow window;

    public BroadcastDispatchSettings(
            @Value("${app.broadcast.providers.brevo.daily-limit:300}") int dailyLimit,
            @Value("${app.broadcast.providers.brevo.reserved-for-transactional:100}") int reservedForTransactional,
            @Value("${app.broadcast.dispatch.max-per-run:100}") int maxPerRun,
            @Value("${app.broadcast.dispatch.window-start:09:00}") String windowStart,
            @Value("${app.broadcast.dispatch.window-end:20:00}") String windowEnd) {
        if (maxPerRun < 1) {
            throw new IllegalArgumentException("The broadcast max per run must be positive");
        }
        this.quota = new BroadcastDailyQuota(dailyLimit, reservedForTransactional);
        this.maxPerRun = maxPerRun;
        this.window = new EmailDispatchWindow(LocalTime.parse(windowStart), LocalTime.parse(windowEnd));
    }

    public BroadcastDailyQuota quota() {
        return quota;
    }

    public int maxPerRun() {
        return maxPerRun;
    }

    public EmailDispatchWindow window() {
        return window;
    }
}
