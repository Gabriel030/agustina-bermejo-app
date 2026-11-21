package com.red_escuela.backend.academicrecord.usecase;

import com.red_escuela.backend.academicrecord.dto.CalendarFeedTokenResponse;
import com.red_escuela.backend.academicrecord.service.RotateCalendarFeedTokenTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RotateMyCalendarFeedTokenService implements RotateMyCalendarFeedTokenUseCase {

    private final RotateCalendarFeedTokenTransaction rotation;

    public RotateMyCalendarFeedTokenService(RotateCalendarFeedTokenTransaction rotation) {
        this.rotation = rotation;
    }

    @Override
    public CalendarFeedTokenResponse rotate(Long studentId) {
        return rotation.rotate(studentId);
    }
}
