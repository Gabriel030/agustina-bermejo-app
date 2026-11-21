package com.red_escuela.backend.academicrecord.usecase;

import com.red_escuela.backend.academicrecord.dto.CalendarFeedStatusResponse;
import com.red_escuela.backend.academicrecord.service.CalendarFeedTokenReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ReadMyCalendarFeedStatusService implements ReadMyCalendarFeedStatusUseCase {

    private final CalendarFeedTokenReader tokenReader;

    public ReadMyCalendarFeedStatusService(CalendarFeedTokenReader tokenReader) {
        this.tokenReader = tokenReader;
    }

    @Override
    public CalendarFeedStatusResponse read(Long studentId) {
        return tokenReader.readStatus(studentId);
    }
}
