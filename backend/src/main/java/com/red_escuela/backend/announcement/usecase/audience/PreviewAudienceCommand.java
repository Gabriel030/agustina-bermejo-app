package com.red_escuela.backend.announcement.usecase.audience;

import com.red_escuela.backend.announcement.entity.AudienceSpec;
import com.red_escuela.backend.announcement.entity.SectionType;

public record PreviewAudienceCommand(SectionType sectionType, Long sectionId, AudienceSpec spec) {
}
