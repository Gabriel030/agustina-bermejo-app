package com.red_escuela.backend.notification.delivery.usecase;

import com.red_escuela.backend.notification.delivery.dto.SimulatedEmailPageResponse;
import com.red_escuela.backend.notification.delivery.specialist.SimulatedEmailListingReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ListSimulatedEmailsService implements ListSimulatedEmailsUseCase {

    private final SimulatedEmailListingReader simulatedEmailListingReader;

    public ListSimulatedEmailsService(SimulatedEmailListingReader simulatedEmailListingReader) {
        this.simulatedEmailListingReader = simulatedEmailListingReader;
    }

    @Override
    public SimulatedEmailPageResponse listSimulatedEmails(int page, int size) {
        return simulatedEmailListingReader.listSimulatedEmails(page, size);
    }
}
