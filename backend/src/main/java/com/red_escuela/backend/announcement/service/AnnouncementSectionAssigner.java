package com.red_escuela.backend.announcement.service;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.SectionType;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

@Service
public class AnnouncementSectionAssigner {

    private final SectionFinder sectionFinder;
    private final EntityManager entityManager;

    public AnnouncementSectionAssigner(SectionFinder sectionFinder, EntityManager entityManager) {
        this.sectionFinder = sectionFinder;
        this.entityManager = entityManager;
    }

    public void assignSection(Announcement announcement, SectionType sectionType, Long sectionId) {
        if (sectionType == SectionType.PROGRAM) {
            sectionFinder.requireExists(SectionKind.PROGRAM, sectionId);
            announcement.setSubjectSection(entityManager.getReference(SubjectSection.class, sectionId));
        } else {
            sectionFinder.requireExists(SectionKind.COURSE, sectionId);
            announcement.setCourseSubjectSection(entityManager.getReference(CourseSubjectSection.class, sectionId));
        }
    }
}
