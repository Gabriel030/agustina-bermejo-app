package com.red_escuela.backend.announcement.specialist;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.SectionType;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StaffSectionAnnouncementReader {

    private final AnnouncementRepository announcementRepository;
    private final SectionFinder sectionFinder;
    private final AnnouncementReadCounter readCounter;
    private final SectionMembershipChecker sectionMembership;

    public StaffSectionAnnouncementReader(AnnouncementRepository announcementRepository,
                                          SectionFinder sectionFinder,
                                          AnnouncementReadCounter readCounter,
                                          SectionMembershipChecker sectionMembership) {
        this.announcementRepository = announcementRepository;
        this.sectionFinder = sectionFinder;
        this.readCounter = readCounter;
        this.sectionMembership = sectionMembership;
    }

    @Transactional(readOnly = true)
    public StaffAnnouncementListing listForProgramSection(Long sectionId) {
        sectionFinder.requireExists(SectionKind.PROGRAM, sectionId);
        return withReadCounts(announcementRepository.findBySubjectSectionIdOrderByCreatedAtDesc(sectionId));
    }

    @Transactional(readOnly = true)
    public StaffAnnouncementListing listForCourseSection(Long sectionId) {
        sectionFinder.requireExists(SectionKind.COURSE, sectionId);
        return withReadCounts(announcementRepository.findByCourseSubjectSectionIdOrderByCreatedAtDesc(sectionId));
    }

    @Transactional(readOnly = true)
    public StaffAnnouncementListing listForProgramSectionAsInstructor(Long sectionId) {
        sectionMembership.requireCurrentInstructorOf(SectionType.PROGRAM, sectionId);
        return listForProgramSection(sectionId);
    }

    @Transactional(readOnly = true)
    public StaffAnnouncementListing listForCourseSectionAsInstructor(Long sectionId) {
        sectionMembership.requireCurrentInstructorOf(SectionType.COURSE, sectionId);
        return listForCourseSection(sectionId);
    }

    private StaffAnnouncementListing withReadCounts(List<Announcement> announcements) {
        return new StaffAnnouncementListing(announcements,
                readCounter.countsOf(announcements.stream().map(Announcement::getId).toList()));
    }
}
