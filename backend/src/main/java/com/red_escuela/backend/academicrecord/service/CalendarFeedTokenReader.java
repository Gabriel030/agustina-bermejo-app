package com.red_escuela.backend.academicrecord.service;

import com.red_escuela.backend.academicrecord.dto.CalendarFeedStatusResponse;
import com.red_escuela.backend.academicrecord.repository.CalendarFeedTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class CalendarFeedTokenReader {

    private final CalendarFeedTokenRepository repository;

    public CalendarFeedTokenReader(CalendarFeedTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public CalendarFeedStatusResponse readStatus(Long studentId) {
        return repository.findByStudentIdAndRevokedAtIsNull(studentId)
                .map(token -> new CalendarFeedStatusResponse(true, token.getCreatedAt(), token.getLastUsedAt()))
                .orElseGet(CalendarFeedStatusResponse::inactive);
    }

    @Transactional(readOnly = true)
    public Optional<ActiveCalendarFeedToken> findActiveByHash(String tokenHash) {
        return repository.findByTokenHashAndRevokedAtIsNull(tokenHash)
                .map(token -> new ActiveCalendarFeedToken(token.getId(), token.getStudentId()));
    }

    public record ActiveCalendarFeedToken(Long id, Long studentId) {
    }
}
