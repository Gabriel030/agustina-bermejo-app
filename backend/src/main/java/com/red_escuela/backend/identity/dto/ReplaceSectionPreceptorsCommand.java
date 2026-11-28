package com.red_escuela.backend.identity.dto;

import java.util.List;

public record ReplaceSectionPreceptorsCommand(Long subjectSectionId, Long courseSectionId, List<Long> personIds, Long actorId) {
}
