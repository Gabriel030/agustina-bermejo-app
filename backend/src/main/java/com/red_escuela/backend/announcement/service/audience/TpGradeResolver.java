package com.red_escuela.backend.announcement.service.audience;

import com.red_escuela.backend.announcement.entity.AudienceSegmentKey;
import com.red_escuela.backend.announcement.entity.AudienceSpec;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class TpGradeResolver implements AudienceResolver {

    private static final Set<AudienceSegmentKey> KEYS = Set.of(
            AudienceSegmentKey.TP_FAILED,
            AudienceSegmentKey.TP_MISSING,
            AudienceSegmentKey.TP_PASSED);

    @Override
    public boolean supports(AudienceSegmentKey key) {
        return KEYS.contains(key);
    }

    @Override
    public boolean matches(StudentSnapshot student, AudienceSpec spec, SectionSnapshot section) {
        double minimum = section.minApprovalGrade();
        return Quantifiers.holds(spec.instanceNumbers(), spec.quantifierOrAny(), instance -> {
            Double score = student.tpScore(instance);
            return switch (spec.getKey()) {
                case TP_FAILED -> score != null && score < minimum;
                case TP_PASSED -> score != null && score >= minimum;
                case TP_MISSING -> !student.tpSubmitted(instance);
                default -> false;
            };
        });
    }

    @Override
    public boolean availableFor(SectionSnapshot section, AudienceSegmentKey key) {
        return section.tpCount() > 0;
    }
}
