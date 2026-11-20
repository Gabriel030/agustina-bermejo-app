package com.red_escuela.backend.notification.broadcast.audience.specialist;

import com.red_escuela.backend.enrollment.api.EnrollmentAudienceFinder;
import com.red_escuela.backend.notification.broadcast.audience.domain.SegmentKey;
import com.red_escuela.backend.notification.broadcast.audience.exception.InvalidSegmentCriterionException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class ProgramSegmentStrategy implements SegmentStrategy {

    private final EnrollmentAudienceFinder finder;

    public ProgramSegmentStrategy(EnrollmentAudienceFinder finder) {
        this.finder = finder;
    }

    private Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException | NullPointerException e) {
            throw new InvalidSegmentCriterionException(SegmentKey.PROGRAM);
        }
    }

    @Override
    public SegmentKey key() {
        return SegmentKey.PROGRAM;
    }

    @Override
    public List<String> parameterNames() {
        return List.of("programId");
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Set<Long> resolvePersonIds(String value) {
        return finder.personIdsInProgram(parseId(value));
    }
}
