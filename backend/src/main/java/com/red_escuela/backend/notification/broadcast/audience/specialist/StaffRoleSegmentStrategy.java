package com.red_escuela.backend.notification.broadcast.audience.specialist;

import com.red_escuela.backend.identity.api.PersonAudienceFinder;
import com.red_escuela.backend.notification.broadcast.audience.domain.SegmentKey;
import com.red_escuela.backend.notification.broadcast.audience.exception.InvalidSegmentCriterionException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class StaffRoleSegmentStrategy implements SegmentStrategy {

    private static final Set<String> STAFF_ROLES = Set.of("DOCENTE", "PRECEPTOR", "ADMINISTRATIVO", "SUBDIRECTOR", "DIRECTOR");

    private final PersonAudienceFinder finder;

    public StaffRoleSegmentStrategy(PersonAudienceFinder finder) {
        this.finder = finder;
    }

    @Override
    public SegmentKey key() {
        return SegmentKey.STAFF_ROLE;
    }

    @Override
    public List<String> parameterNames() {
        return List.of("role");
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Set<Long> resolvePersonIds(String value) {
        if (value == null || !STAFF_ROLES.contains(value)) {
            throw new InvalidSegmentCriterionException(SegmentKey.STAFF_ROLE);
        }
        return finder.personIdsByRoleName(value);
    }
}
