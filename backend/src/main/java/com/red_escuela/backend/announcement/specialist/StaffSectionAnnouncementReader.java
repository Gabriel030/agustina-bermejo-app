package com.red_escuela.backend.announcement.specialist;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StaffSectionAnnouncementReader {

    private final AnnouncementRepository announcementRepository;
    private final SectionFinder sectionFinder;
    private final AnnouncementReadCounter readCounter;

    public StaffSectionAnnouncementReader(AnnouncementRepository announcementRepository,
                                          SectionFinder sectionFinder,
                                          AnnouncementReadCounter readCounter) {
        this.announcementRepository = announcementRepository;
        this.sectionFinder = sectionFinder;
        this.readCounter = readCounter;
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

    private StaffAnnouncementListing withReadCounts(List<Announcement> announcements) {
        return new StaffAnnouncementListing(announcements,
                readCounter.countsOf(announcements.stream().map(Announcement::getId).toList()));
    }
}
