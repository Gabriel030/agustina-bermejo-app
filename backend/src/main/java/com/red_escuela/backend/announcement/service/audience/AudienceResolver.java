package com.red_escuela.backend.announcement.service.audience;

import com.red_escuela.backend.announcement.entity.AudienceSegmentKey;
import com.red_escuela.backend.announcement.entity.AudienceSpec;

public interface AudienceResolver {

    boolean supports(AudienceSegmentKey key);

    boolean matches(StudentSnapshot student, AudienceSpec spec, SectionSnapshot section);

    boolean availableFor(SectionSnapshot section, AudienceSegmentKey key);
}
