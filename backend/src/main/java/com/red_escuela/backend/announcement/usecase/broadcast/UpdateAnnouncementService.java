package com.red_escuela.backend.announcement.usecase.broadcast;

import com.red_escuela.backend.shared.time.ApplicationTime;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.specialist.UpdateAnnouncementTransaction;
import com.red_escuela.backend.shared.UseCase;


@UseCase
public class UpdateAnnouncementService implements UpdateAnnouncementUseCase {

    private final UpdateAnnouncementTransaction updateAnnouncementTransaction;

    public UpdateAnnouncementService(UpdateAnnouncementTransaction updateAnnouncementTransaction) {
        this.updateAnnouncementTransaction = updateAnnouncementTransaction;
    }

    @Override
    public Announcement update(UpdateAnnouncementCommand command) {
        return updateAnnouncementTransaction.update(command.announcementId(), command.title(), command.body(),
                command.category(), command.urgent(), command.expiresAt(), command.scheduledFor(),
                command.audienceSpec(), command.notifyRecipients(), ApplicationTime.now());
    }
}
