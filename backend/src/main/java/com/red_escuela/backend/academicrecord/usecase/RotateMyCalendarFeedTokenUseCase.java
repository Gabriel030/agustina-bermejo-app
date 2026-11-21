package com.red_escuela.backend.academicrecord.usecase;

import com.red_escuela.backend.academicrecord.dto.CalendarFeedTokenResponse;

public interface RotateMyCalendarFeedTokenUseCase {

    CalendarFeedTokenResponse rotate(Long studentId);
}
