package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.SectionType;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.announcement.service.SectionMembership;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListSectionAnnouncementsService implements ListSectionAnnouncementsUseCase {

    private final AnnouncementRepository announcementRepository;
    private final SectionMembership sectionMembership;

    public ListSectionAnnouncementsService(AnnouncementRepository announcementRepository,
                                           SectionMembership sectionMembership) {
        this.announcementRepository = announcementRepository;
        this.sectionMembership = sectionMembership;
    }

    @Override
    public List<Announcement> list(SectionType sectionType, Long sectionId) {
        sectionMembership.requireCurrentInstructorOf(sectionType, sectionId);
        return sectionType == SectionType.PROGRAM
                ? announcementRepository.findBySubjectSectionIdOrderByCreatedAtDesc(sectionId)
                : announcementRepository.findByCourseSubjectSectionIdOrderByCreatedAtDesc(sectionId);
    }
}
