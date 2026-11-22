package com.red_escuela.backend.notification.broadcast.audience.specialist;

import com.red_escuela.backend.identity.api.PersonAudienceFinder;
import com.red_escuela.backend.notification.broadcast.audience.exception.InvalidSegmentCriterionException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StaffRoleSegmentStrategySubdirectorTest {

    private final PersonAudienceFinder finder = mock(PersonAudienceFinder.class);
    private final StaffRoleSegmentStrategy strategy = new StaffRoleSegmentStrategy(finder);

    @Test
    void subdirectorIsAResolvableStaffRole() {
        when(finder.personIdsByRoleName("SUBDIRECTOR")).thenReturn(Set.of(9L));

        assertThat(strategy.resolvePersonIds("SUBDIRECTOR")).containsExactly(9L);
    }

    @Test
    void unknownRoleIsStillRejected() {
        assertThatThrownBy(() -> strategy.resolvePersonIds("PLATFORM_ADMIN")).isInstanceOf(InvalidSegmentCriterionException.class);
    }
}
