package com.red_escuela.backend.notification.broadcast.audience.specialist;

import com.red_escuela.backend.billing.api.OverdueDebtAudience;
import com.red_escuela.backend.identity.api.PersonAudienceFinder;
import com.red_escuela.backend.notification.broadcast.audience.exception.SegmentNotAvailableException;
import com.red_escuela.backend.notification.broadcast.audience.domain.SegmentKey;
import com.red_escuela.backend.notification.broadcast.audience.exception.InvalidSegmentCriterionException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class OverdueDebtSegmentStrategy implements SegmentStrategy {

    private final OverdueDebtAudience overdueDebtAudience;
    private final PersonAudienceFinder personAudienceFinder;

    public OverdueDebtSegmentStrategy(OverdueDebtAudience overdueDebtAudience, PersonAudienceFinder personAudienceFinder) {
        this.overdueDebtAudience = overdueDebtAudience;
        this.personAudienceFinder = personAudienceFinder;
    }

    @Override
    public SegmentKey key() {
        return SegmentKey.OVERDUE_DEBT;
    }

    @Override
    public List<String> parameterNames() {
        return List.of();
    }

    @Override
    public boolean isAvailable() {
        return overdueDebtAudience.isAvailable();
    }

    @Override
    public Set<Long> resolvePersonIds(String value) {
        return overdueDebtAudience.overdueStudentIds()
                .map(personAudienceFinder::personIdsOfStudents)
                .orElseThrow(() -> new SegmentNotAvailableException(SegmentKey.OVERDUE_DEBT));
    }
}
