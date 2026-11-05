package com.red_escuela.backend.announcement.service.audience;

import com.red_escuela.backend.announcement.entity.AudienceSegmentKey;
import com.red_escuela.backend.announcement.entity.AudienceSpec;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class CourseworkStatusResolver implements AudienceResolver {

    private static final Set<AudienceSegmentKey> KEYS = Set.of(
            AudienceSegmentKey.MUST_TAKE_FINAL,
            AudienceSegmentKey.PROMOTED,
            AudienceSegmentKey.PASSED,
            AudienceSegmentKey.FAILED_COURSE);

    @Override
    public boolean supports(AudienceSegmentKey key) {
        return KEYS.contains(key);
    }

    @Override
    public boolean matches(StudentSnapshot student, AudienceSpec spec, SectionSnapshot section) {
        return switch (spec.getKey()) {
            case MUST_TAKE_FINAL -> student.status() == EnrollmentStatus.REGULAR;
            case PROMOTED -> student.status() == EnrollmentStatus.APPROVED
                    && student.finalGradeType() == FinalGradeType.PROMOTION;
            case PASSED -> student.status() == EnrollmentStatus.APPROVED;
            case FAILED_COURSE -> student.status() == EnrollmentStatus.FAILED;
            default -> false;
        };
    }

    @Override
    public boolean availableFor(SectionSnapshot section, AudienceSegmentKey key) {
        return true;
    }
}
