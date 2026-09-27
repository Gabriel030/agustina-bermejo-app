package com.red_escuela.backend.announcement.usecase.audience;

import com.red_escuela.backend.announcement.dto.AudienceSegmentResponse;
import com.red_escuela.backend.announcement.entity.SectionType;

import java.util.List;

public interface ListAudienceSegmentsUseCase {

    List<AudienceSegmentResponse> list(SectionType sectionType, Long sectionId);
}
