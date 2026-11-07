package com.red_escuela.backend.announcement.usecase.broadcast;

import com.red_escuela.backend.announcement.dto.AnnouncementResponse;
import com.red_escuela.backend.announcement.specialist.StaffSectionAnnouncementReader;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListStaffSectionAnnouncementsService implements ListStaffSectionAnnouncementsUseCase {

    private final StaffSectionAnnouncementReader announcementReader;

    public ListStaffSectionAnnouncementsService(StaffSectionAnnouncementReader announcementReader) {
        this.announcementReader = announcementReader;
    }

    @Override
    public List<AnnouncementResponse> listForProgramSection(Long sectionId) {
        return AnnouncementResponse.fromListing(announcementReader.listForProgramSection(sectionId));
    }

    @Override
    public List<AnnouncementResponse> listForCourseSection(Long sectionId) {
        return AnnouncementResponse.fromListing(announcementReader.listForCourseSection(sectionId));
    }
}
