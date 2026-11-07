package com.red_escuela.backend.announcement.usecase.broadcast;

import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.SectionType;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.announcement.specialist.SectionMembershipChecker;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListSectionAnnouncementsService implements ListSectionAnnouncementsUseCase {

    private final AnnouncementRepository announcementRepository;
    private final SectionMembershipChecker sectionMembership;

    public ListSectionAnnouncementsService(AnnouncementRepository announcementRepository,
                                           SectionMembershipChecker sectionMembership) {
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
