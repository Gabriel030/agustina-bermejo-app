package com.red_escuela.backend.academicstructure.api;

import java.util.Collection;
import java.util.Map;

public interface SectionPendingAttendanceRequestsPort {
    Map<Long, Long> countPendingBySubjectSectionIds(Collection<Long> subjectSectionIds);

    Map<Long, Long> countPendingByCourseSectionIds(Collection<Long> courseSectionIds);
}
