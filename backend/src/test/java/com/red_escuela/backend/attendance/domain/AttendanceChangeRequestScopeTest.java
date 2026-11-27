package com.red_escuela.backend.attendance.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceChangeRequestScopeTest {

    @Test
    void unrestrictedScopeCoversAnyProgramAndCourse() {
        assertThat(AttendanceChangeRequestScope.everything().covers(1L, null)).isTrue();
        assertThat(AttendanceChangeRequestScope.everything().covers(null, 9L)).isTrue();
    }

    @Test
    void limitedScopeCoversOnlyAssignedPrograms() {
        AttendanceChangeRequestScope scope = AttendanceChangeRequestScope.limitedTo(Set.of(1L), Set.of());

        assertThat(scope.covers(1L, null)).isTrue();
        assertThat(scope.covers(2L, null)).isFalse();
    }

    @Test
    void limitedScopeCoversOnlyAssignedCourses() {
        AttendanceChangeRequestScope scope = AttendanceChangeRequestScope.limitedTo(Set.of(), Set.of(5L));

        assertThat(scope.covers(null, 5L)).isTrue();
        assertThat(scope.covers(null, 6L)).isFalse();
    }

    @Test
    void emptyLimitedScopeCoversNothingAndQueriesWithAPlaceholderId() {
        AttendanceChangeRequestScope scope = AttendanceChangeRequestScope.limitedTo(Set.of(), Set.of());

        assertThat(scope.covers(1L, 1L)).isFalse();
        assertThat(scope.programIdsForQuery()).containsExactly(-1L);
        assertThat(scope.courseIdsForQuery()).containsExactly(-1L);
    }
}
