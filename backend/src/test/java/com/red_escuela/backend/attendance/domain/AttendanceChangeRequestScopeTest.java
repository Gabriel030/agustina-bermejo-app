package com.red_escuela.backend.attendance.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceChangeRequestScopeTest {

    @Test
    void everythingScopeIsUnrestricted() {
        assertThat(AttendanceChangeRequestScope.everything().unrestricted()).isTrue();
    }

    @Test
    void limitedScopeIsRestrictedAndKeepsItsAssignedIds() {
        AttendanceChangeRequestScope scope = AttendanceChangeRequestScope.limitedTo(Set.of(1L), Set.of(2L), Set.of(3L));

        assertThat(scope.unrestricted()).isFalse();
        assertThat(scope.subjectSectionIdsForQuery()).containsExactly(1L);
        assertThat(scope.courseSectionIdsForQuery()).containsExactly(2L);
        assertThat(scope.programCohortIdsForQuery()).containsExactly(3L);
    }

    @Test
    void emptyLimitedScopeQueriesWithAPlaceholderIdThatMatchesNothing() {
        AttendanceChangeRequestScope scope = AttendanceChangeRequestScope.limitedTo(Set.of(), Set.of(), Set.of());

        assertThat(scope.subjectSectionIdsForQuery()).containsExactly(-1L);
        assertThat(scope.courseSectionIdsForQuery()).containsExactly(-1L);
        assertThat(scope.programCohortIdsForQuery()).containsExactly(-1L);
    }
}
