package com.red_escuela.backend.announcement.usecase.broadcast;

import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.specialist.CreateAnnouncementTransaction;
import com.red_escuela.backend.shared.UseCase;

import java.time.LocalDateTime;

@UseCase
public class CreateAnnouncementService implements CreateAnnouncementUseCase {

    private final CreateAnnouncementTransaction createAnnouncementTransaction;

    public CreateAnnouncementService(CreateAnnouncementTransaction createAnnouncementTransaction) {
        this.createAnnouncementTransaction = createAnnouncementTransaction;
    }

    @Override
    public Announcement create(CreateAnnouncementCommand command) {
        return createAnnouncementTransaction.create(command.sectionType(), command.sectionId(), command.title(),
                command.body(), command.category(), command.urgent(), command.expiresAt(), command.scheduledFor(),
                command.audienceSpec(), LocalDateTime.now());
    }
}
