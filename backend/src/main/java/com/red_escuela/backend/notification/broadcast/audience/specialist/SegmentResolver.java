package com.red_escuela.backend.notification.broadcast.audience.specialist;

import com.red_escuela.backend.notification.broadcast.audience.domain.SegmentDescriptor;
import com.red_escuela.backend.notification.broadcast.audience.domain.SegmentKey;
import com.red_escuela.backend.notification.broadcast.audience.domain.SegmentQuantifier;
import com.red_escuela.backend.notification.broadcast.audience.dto.SegmentCriterionRequest;
import com.red_escuela.backend.notification.broadcast.audience.dto.SegmentSpecRequest;
import com.red_escuela.backend.notification.broadcast.audience.exception.SegmentNotAvailableException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SegmentResolver {

    private final Map<SegmentKey, SegmentStrategy> strategies;

    public SegmentResolver(List<SegmentStrategy> strategies) {
        this.strategies = strategies.stream().collect(Collectors.toMap(SegmentStrategy::key, Function.identity()));
    }

    public List<SegmentDescriptor> availableSegments() {
        return strategies.values().stream()
                .filter(SegmentStrategy::isAvailable)
                .map(strategy -> new SegmentDescriptor(strategy.key(), strategy.parameterNames()))
                .sorted(java.util.Comparator.comparing(descriptor -> descriptor.key().ordinal()))
                .toList();
    }

    public Set<Long> resolvePersonIds(SegmentSpecRequest spec) {
        Set<Long> result = null;
        for (SegmentCriterionRequest criterion : spec.criteria()) {
            SegmentStrategy strategy = strategies.get(criterion.key());
            if (strategy == null || !strategy.isAvailable()) {
                throw new SegmentNotAvailableException(criterion.key());
            }
            Set<Long> matched = strategy.resolvePersonIds(criterion.value());
            if (result == null) {
                result = new HashSet<>(matched);
            } else if (spec.quantifier() == SegmentQuantifier.ALL) {
                result.retainAll(matched);
            } else {
                result.addAll(matched);
            }
        }
        return result == null ? Set.of() : result;
    }
}
