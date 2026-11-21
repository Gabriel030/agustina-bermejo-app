package com.red_escuela.backend.academicrecord.service;

import com.red_escuela.backend.academicrecord.repository.CalendarFeedTokenRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class CalendarFeedTokenUsageRecorder {

    private static final Duration MIN_INTERVAL_BETWEEN_UPDATES = Duration.ofMinutes(10);

    private final CalendarFeedTokenRepository repository;
    private final Clock clock;

    public CalendarFeedTokenUsageRecorder(CalendarFeedTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void recordUsage(Long tokenId) {
        LocalDateTime now = LocalDateTime.now(clock);
        repository.touchLastUsedAt(tokenId, now, now.minus(MIN_INTERVAL_BETWEEN_UPDATES));
    }
}
