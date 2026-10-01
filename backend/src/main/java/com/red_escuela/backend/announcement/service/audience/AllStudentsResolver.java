package com.red_escuela.backend.announcement.service.audience;

import com.red_escuela.backend.announcement.entity.AudienceSegmentKey;
import com.red_escuela.backend.announcement.entity.AudienceSpec;
import org.springframework.stereotype.Component;

@Component
public class AllStudentsResolver implements AudienceResolver {

    @Override
    public boolean supports(AudienceSegmentKey key) {
        return key == AudienceSegmentKey.ALL;
    }

    @Override
    public boolean matches(StudentSnapshot student, AudienceSpec spec, SectionSnapshot section) {
        return true;
    }

    @Override
    public boolean availableFor(SectionSnapshot section, AudienceSegmentKey key) {
        return true;
    }
}
