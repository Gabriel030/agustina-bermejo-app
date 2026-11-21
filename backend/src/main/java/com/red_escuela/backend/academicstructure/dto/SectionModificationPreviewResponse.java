package com.red_escuela.backend.academicstructure.dto;

import java.util.List;

public record SectionModificationPreviewResponse(List<SectionModificationConflictResponse> conflicts) {
}
