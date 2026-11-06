package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.announcement.service.StaffAnnouncementListing;
import com.red_escuela.backend.announcement.service.StaffSectionAnnouncementReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ListStaffSectionAnnouncementsService implements ListStaffSectionAnnouncementsUseCase {

    private final StaffSectionAnnouncementReader announcementReader;

    public ListStaffSectionAnnouncementsService(StaffSectionAnnouncementReader announcementReader) {
        this.announcementReader = announcementReader;
    }

    @Override
    public StaffAnnouncementListing listForProgramSection(Long sectionId) {
        return announcementReader.listForProgramSection(sectionId);
    }

    @Override
    public StaffAnnouncementListing listForCourseSection(Long sectionId) {
        return announcementReader.listForCourseSection(sectionId);
    }
}
