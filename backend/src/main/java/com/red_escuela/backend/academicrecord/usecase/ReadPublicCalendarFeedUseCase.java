package com.red_escuela.backend.academicrecord.usecase;

public interface ReadPublicCalendarFeedUseCase {

    byte[] read(String rawToken, String clientAddress);
}
