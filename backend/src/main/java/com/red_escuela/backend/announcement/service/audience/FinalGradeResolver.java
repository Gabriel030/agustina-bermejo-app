package com.red_escuela.backend.announcement.service.audience;

import com.red_escuela.backend.announcement.entity.AudienceSegmentKey;
import com.red_escuela.backend.announcement.entity.AudienceSpec;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class FinalGradeResolver implements AudienceResolver {

    private static final Set<AudienceSegmentKey> KEYS = Set.of(
            AudienceSegmentKey.FINAL_PASSED,
            AudienceSegmentKey.FINAL_FAILED);

    @Override
    public boolean supports(AudienceSegmentKey key) {
        return KEYS.contains(key);
    }

    @Override
    public boolean matches(StudentSnapshot student, AudienceSpec spec, SectionSnapshot section) {
        double minimum = section.minApprovalGrade();
        return Quantifiers.holds(spec.instanceNumbers(), spec.quantifierOrAny(), call -> {
            Double score = student.finalScore(call);
            return switch (spec.getKey()) {
                case FINAL_PASSED -> score != null && score >= minimum;
                case FINAL_FAILED -> score != null && score < minimum;
                default -> false;
            };
        });
    }

    @Override
    public boolean availableFor(SectionSnapshot section, AudienceSegmentKey key) {
        return section.hasEvaluationScheme() && Boolean.TRUE.equals(section.evaluationScheme().getHasFinal());
    }
}
