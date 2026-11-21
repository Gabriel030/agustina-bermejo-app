package com.red_escuela.backend.academicrecord.usecase;

import com.red_escuela.backend.academicrecord.dto.CalendarFeedStatusResponse;

public interface ReadMyCalendarFeedStatusUseCase {

    CalendarFeedStatusResponse read(Long studentId);
}
