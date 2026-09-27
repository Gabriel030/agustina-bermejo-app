package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.SectionType;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.announcement.service.SectionMembership;
import com.red_escuela.backend.service.CurrentInstructorService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class ListSectionAnnouncementsService implements ListSectionAnnouncementsUseCase {

    private final AnnouncementRepository announcementRepository;
    private final SectionMembership sectionMembership;
    private final CurrentInstructorService currentInstructorService;

    public ListSectionAnnouncementsService(AnnouncementRepository announcementRepository,
                                           SectionMembership sectionMembership,
                                           CurrentInstructorService currentInstructorService) {
        this.announcementRepository = announcementRepository;
        this.sectionMembership = sectionMembership;
        this.currentInstructorService = currentInstructorService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Announcement> list(SectionType sectionType, Long sectionId) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        sectionMembership.requireInstructorOf(sectionType, sectionId, instructor.getId());
        return sectionType == SectionType.PROGRAM
                ? announcementRepository.findBySubjectSectionIdOrderByCreatedAtDesc(sectionId)
                : announcementRepository.findByCourseSubjectSectionIdOrderByCreatedAtDesc(sectionId);
    }
}
