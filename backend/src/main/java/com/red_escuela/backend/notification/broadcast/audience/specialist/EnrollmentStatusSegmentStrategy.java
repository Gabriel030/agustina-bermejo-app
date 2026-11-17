package com.red_escuela.backend.notification.broadcast.audience.specialist;

import com.red_escuela.backend.enrollment.api.EnrollmentAudienceFinder;
import com.red_escuela.backend.notification.broadcast.audience.domain.SegmentKey;
import com.red_escuela.backend.notification.broadcast.audience.exception.InvalidSegmentCriterionException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class EnrollmentStatusSegmentStrategy implements SegmentStrategy {

    private final EnrollmentAudienceFinder finder;

    public EnrollmentStatusSegmentStrategy(EnrollmentAudienceFinder finder) {
        this.finder = finder;
    }

    @Override
    public SegmentKey key() {
        return SegmentKey.ENROLLMENT_STATUS;
    }

    @Override
    public List<String> parameterNames() {
        return List.of("status");
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Set<Long> resolvePersonIds(String value) {
        if (value == null) {
            throw new InvalidSegmentCriterionException(SegmentKey.ENROLLMENT_STATUS);
        }
        return switch (value) {
            case "IN_COURSE" -> finder.personIdsInCourse();
            case "REGULAR" -> finder.personIdsWithRegularStatus();
            case "GRADUATED" -> finder.personIdsGraduated();
            case "WITHDRAWN" -> finder.personIdsWithdrawn();
            default -> throw new InvalidSegmentCriterionException(SegmentKey.ENROLLMENT_STATUS);
        };
    }
}
