package com.red_escuela.backend.announcement.dto;

import java.util.List;

public record AudiencePreviewResponse(
        int count,
        int totalInSection,
        String label,
        boolean hasUngradedInstances,
        List<AudienceStudentResponse> students) {
}
