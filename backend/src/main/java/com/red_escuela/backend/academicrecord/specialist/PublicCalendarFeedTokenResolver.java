package com.red_escuela.backend.academicrecord.specialist;

import com.red_escuela.backend.academicrecord.exception.CalendarFeedNotFoundException;
import com.red_escuela.backend.academicrecord.service.CalendarFeedTokenReader;
import com.red_escuela.backend.academicrecord.service.CalendarFeedTokenReader.ActiveCalendarFeedToken;
import com.red_escuela.backend.academicrecord.service.CalendarFeedTokenUsageRecorder;
import org.springframework.stereotype.Component;

@Component
public class PublicCalendarFeedTokenResolver {

    private static final String NEVER_ISSUED_TOKEN = "0".repeat(CalendarFeedTokenFactory.TOKEN_LENGTH);

    private final CalendarFeedRateLimiter rateLimiter;
    private final CalendarFeedTokenReader tokenReader;
    private final CalendarFeedTokenUsageRecorder usageRecorder;

    public PublicCalendarFeedTokenResolver(CalendarFeedRateLimiter rateLimiter, CalendarFeedTokenReader tokenReader,
                                           CalendarFeedTokenUsageRecorder usageRecorder) {
        this.rateLimiter = rateLimiter;
        this.tokenReader = tokenReader;
        this.usageRecorder = usageRecorder;
    }

    public ActiveCalendarFeedToken resolveStudentToken(String rawToken, String clientAddress) {
        rateLimiter.assertClientIsNotBlocked(clientAddress);
        String candidate = CalendarFeedTokenFactory.isWellFormed(rawToken) ? rawToken : NEVER_ISSUED_TOKEN;
        String tokenHash = CalendarFeedTokenHasher.hash(candidate);
        ActiveCalendarFeedToken active = tokenReader.findActiveByHash(tokenHash).orElse(null);
        if (active == null) {
            rateLimiter.chargeMiss(clientAddress);
            throw new CalendarFeedNotFoundException();
        }
        rateLimiter.acquireForToken(tokenHash);
        usageRecorder.recordUsage(active.id());
        return active;
    }
}
