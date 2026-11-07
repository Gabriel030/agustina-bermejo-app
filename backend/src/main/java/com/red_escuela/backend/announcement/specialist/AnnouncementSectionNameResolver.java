package com.red_escuela.backend.announcement.specialist;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;

public final class AnnouncementSectionNameResolver {

    private AnnouncementSectionNameResolver() {
    }

    public static String of(SubjectSection section) {
        return section.getSubject().getName();
    }

    public static String of(CourseSubjectSection section) {
        return section.getModuleName();
    }
}
