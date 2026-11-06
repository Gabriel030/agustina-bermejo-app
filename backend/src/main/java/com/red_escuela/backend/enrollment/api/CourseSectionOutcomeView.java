package com.red_escuela.backend.enrollment.api;

import java.time.LocalDate;

public record CourseSectionOutcomeView(int gradedSections, int approvedSections, LocalDate lastTermEndDate) {

    public boolean allApproved() {
        return gradedSections > 0 && approvedSections == gradedSections;
    }
}
