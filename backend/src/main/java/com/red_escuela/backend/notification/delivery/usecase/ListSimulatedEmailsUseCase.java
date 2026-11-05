package com.red_escuela.backend.notification.delivery.usecase;

import com.red_escuela.backend.notification.delivery.dto.SimulatedEmailPageResponse;

public interface ListSimulatedEmailsUseCase {
    SimulatedEmailPageResponse listSimulatedEmails(int page, int size);
}
