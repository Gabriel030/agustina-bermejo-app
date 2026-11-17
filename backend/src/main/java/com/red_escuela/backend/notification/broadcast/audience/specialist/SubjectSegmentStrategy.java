package com.red_escuela.backend.notification.broadcast.audience.specialist;

import com.red_escuela.backend.enrollment.api.EnrollmentAudienceFinder;
import com.red_escuela.backend.notification.broadcast.audience.domain.SegmentKey;
import com.red_escuela.backend.notification.broadcast.audience.exception.InvalidSegmentCriterionException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class SubjectSegmentStrategy implements SegmentStrategy {

    private final EnrollmentAudienceFinder finder;

    public SubjectSegmentStrategy(EnrollmentAudienceFinder finder) {
        this.finder = finder;
    }

    private Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException | NullPointerException e) {
            throw new InvalidSegmentCriterionException(SegmentKey.SUBJECT);
        }
    }

    @Override
    public SegmentKey key() {
        return SegmentKey.SUBJECT;
    }

    @Override
    public List<String> parameterNames() {
        return List.of("subjectId");
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Set<Long> resolvePersonIds(String value) {
        return finder.personIdsInSubject(parseId(value));
    }
}
