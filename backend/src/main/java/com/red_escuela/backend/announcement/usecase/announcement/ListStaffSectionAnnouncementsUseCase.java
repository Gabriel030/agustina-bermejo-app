package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.announcement.dto.AnnouncementResponse;

import java.util.List;

public interface ListStaffSectionAnnouncementsUseCase {

    List<AnnouncementResponse> listForProgramSection(Long sectionId);

    List<AnnouncementResponse> listForCourseSection(Long sectionId);
}
