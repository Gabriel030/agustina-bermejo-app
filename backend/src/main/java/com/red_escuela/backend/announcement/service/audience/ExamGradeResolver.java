package com.red_escuela.backend.announcement.service.audience;

import com.red_escuela.backend.announcement.entity.AudienceSegmentKey;
import com.red_escuela.backend.announcement.entity.AudienceSpec;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ExamGradeResolver implements AudienceResolver {

    private static final Set<AudienceSegmentKey> KEYS = Set.of(
            AudienceSegmentKey.EXAM_FAILED,
            AudienceSegmentKey.EXAM_PASSED,
            AudienceSegmentKey.EXAM_MISSING);

    @Override
    public boolean supports(AudienceSegmentKey key) {
        return KEYS.contains(key);
    }

    @Override
    public boolean matches(StudentSnapshot student, AudienceSpec spec, SectionSnapshot section) {
        double minimum = section.minApprovalGrade();
        return Quantifiers.holds(spec.instanceNumbers(), spec.quantifierOrAny(), instance -> {
            Double score = student.examScore(instance);
            return switch (spec.getKey()) {
                case EXAM_FAILED -> score != null && score < minimum;
                case EXAM_PASSED -> score != null && score >= minimum;
                case EXAM_MISSING -> score == null;
                default -> false;
            };
        });
    }

    @Override
    public boolean availableFor(SectionSnapshot section, AudienceSegmentKey key) {
        return section.examCount() > 0;
    }
}
