package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.SectionType;

import java.util.List;

public interface ListSectionAnnouncementsUseCase {

    List<Announcement> list(SectionType sectionType, Long sectionId);
}
