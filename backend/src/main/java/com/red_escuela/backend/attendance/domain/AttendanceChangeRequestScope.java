package com.red_escuela.backend.attendance.domain;

import java.util.Set;

public record AttendanceChangeRequestScope(boolean unrestricted, Set<Long> subjectSectionIds, Set<Long> courseSectionIds, Set<Long> programCohortIds) {

    private static final Long NO_ID = -1L;

    public AttendanceChangeRequestScope {
        subjectSectionIds = Set.copyOf(subjectSectionIds);
        courseSectionIds = Set.copyOf(courseSectionIds);
        programCohortIds = Set.copyOf(programCohortIds);
    }

    public static AttendanceChangeRequestScope everything() {
        return new AttendanceChangeRequestScope(true, Set.of(), Set.of(), Set.of());
    }

    public static AttendanceChangeRequestScope limitedTo(Set<Long> subjectSectionIds, Set<Long> courseSectionIds, Set<Long> programCohortIds) {
        return new AttendanceChangeRequestScope(false, subjectSectionIds, courseSectionIds, programCohortIds);
    }

    public Set<Long> subjectSectionIdsForQuery() {
        return subjectSectionIds.isEmpty() ? Set.of(NO_ID) : subjectSectionIds;
    }

    public Set<Long> courseSectionIdsForQuery() {
        return courseSectionIds.isEmpty() ? Set.of(NO_ID) : courseSectionIds;
    }

    public Set<Long> programCohortIdsForQuery() {
        return programCohortIds.isEmpty() ? Set.of(NO_ID) : programCohortIds;
    }
}
