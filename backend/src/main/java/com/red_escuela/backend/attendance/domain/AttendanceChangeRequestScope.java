package com.red_escuela.backend.attendance.domain;

import java.util.Set;

public record AttendanceChangeRequestScope(boolean unrestricted, Set<Long> programIds, Set<Long> courseIds) {

    private static final Long NO_ID = -1L;

    public AttendanceChangeRequestScope {
        programIds = Set.copyOf(programIds);
        courseIds = Set.copyOf(courseIds);
    }

    public static AttendanceChangeRequestScope everything() {
        return new AttendanceChangeRequestScope(true, Set.of(), Set.of());
    }

    public static AttendanceChangeRequestScope limitedTo(Set<Long> programIds, Set<Long> courseIds) {
        return new AttendanceChangeRequestScope(false, programIds, courseIds);
    }

    public Set<Long> programIdsForQuery() {
        return programIds.isEmpty() ? Set.of(NO_ID) : programIds;
    }

    public Set<Long> courseIdsForQuery() {
        return courseIds.isEmpty() ? Set.of(NO_ID) : courseIds;
    }

    public boolean covers(Long programId, Long courseId) {
        return unrestricted
                || (programId != null && programIds.contains(programId))
                || (courseId != null && courseIds.contains(courseId));
    }
}
