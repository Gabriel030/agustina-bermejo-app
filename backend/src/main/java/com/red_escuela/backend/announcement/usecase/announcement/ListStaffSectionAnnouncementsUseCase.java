package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.announcement.service.StaffAnnouncementListing;

public interface ListStaffSectionAnnouncementsUseCase {

    StaffAnnouncementListing listForProgramSection(Long sectionId);

    StaffAnnouncementListing listForCourseSection(Long sectionId);
}
