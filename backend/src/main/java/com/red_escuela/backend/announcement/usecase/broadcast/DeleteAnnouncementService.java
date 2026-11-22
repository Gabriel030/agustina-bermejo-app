package com.red_escuela.backend.announcement.usecase.broadcast;

import com.red_escuela.backend.announcement.specialist.DeleteAnnouncementTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class DeleteAnnouncementService implements DeleteAnnouncementUseCase {

    private final DeleteAnnouncementTransaction deleteAnnouncementTransaction;

    public DeleteAnnouncementService(DeleteAnnouncementTransaction deleteAnnouncementTransaction) {
        this.deleteAnnouncementTransaction = deleteAnnouncementTransaction;
    }

    @Override
    public void delete(Long announcementId) {
        deleteAnnouncementTransaction.delete(announcementId);
    }
}
