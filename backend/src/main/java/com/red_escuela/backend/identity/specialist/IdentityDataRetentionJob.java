package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.shared.retention.RetentionBatchPurger;
import com.red_escuela.backend.shared.retention.RetentionSettings;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class IdentityDataRetentionJob {

    private final RetentionBatchPurger purger;
    private final RetentionSettings settings;
    private final Clock clock;
    private final Duration passwordResetFailureRetention;

    public IdentityDataRetentionJob(RetentionBatchPurger purger, RetentionSettings settings, Clock clock,
                                    @Value("${auth.password-reset-lockout.duration-minutes}") long sourceWindowMinutes,
                                    @Value("${auth.password-reset-lockout.account-window-hours}") long accountWindowHours) {
        this.passwordResetFailureRetention = Duration.ofMinutes(sourceWindowMinutes).compareTo(Duration.ofHours(accountWindowHours)) > 0
                ? Duration.ofMinutes(sourceWindowMinutes) : Duration.ofHours(accountWindowHours);
        this.purger = purger;
        this.settings = settings;
        this.clock = clock;
    }

    @Scheduled(cron = "0 40 3 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "IdentityDataRetentionJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void run() {
        deleteExpiredIdentityData();
    }

    public int deleteExpiredIdentityData() {
        if (!settings.enabled()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        MapSqlParameterSource expired = new MapSqlParameterSource("now", now);
        int deleted = purger.purge("refresh_token", "refresh_token", "expires_at < :now", expired);
        deleted += purger.purge("password_reset_token", "password_reset_token", "expires_at < :now", expired);
        deleted += purger.purge("email_verification_token", "email_verification_token", "expires_at < :now", expired);
        deleted += purger.purge("login_attempt", "login_attempt", "attempted_at < :loginAttemptCutoff",
                new MapSqlParameterSource("loginAttemptCutoff", now.minusMonths(settings.loginAttemptMonths())));
        deleted += purger.purge("password_reset_failure", "password_reset_failure", "person_id, source_hash",
                "window_start <= :failureCutoff",
                new MapSqlParameterSource("failureCutoff", now.minus(passwordResetFailureRetention)));
        return deleted;
    }
}
