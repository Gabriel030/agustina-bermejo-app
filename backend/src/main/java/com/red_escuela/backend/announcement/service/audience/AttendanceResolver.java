package com.red_escuela.backend.announcement.service.audience;

import com.red_escuela.backend.announcement.entity.AudienceSegmentKey;
import com.red_escuela.backend.announcement.entity.AudienceSpec;
import org.springframework.stereotype.Component;

@Component
public class AttendanceResolver implements AudienceResolver {

    private static final int DEFAULT_THRESHOLD = 3;

    @Override
    public boolean supports(AudienceSegmentKey key) {
        return key == AudienceSegmentKey.ABSENCES;
    }

    @Override
    public boolean matches(StudentSnapshot student, AudienceSpec spec, SectionSnapshot section) {
        int threshold = spec.getThreshold() == null ? DEFAULT_THRESHOLD : spec.getThreshold();
        return student.absences() >= threshold;
    }

    @Override
    public boolean availableFor(SectionSnapshot section, AudienceSegmentKey key) {
        return section.students().stream().anyMatch(s -> s.recordedClasses() > 0);
    }
}
