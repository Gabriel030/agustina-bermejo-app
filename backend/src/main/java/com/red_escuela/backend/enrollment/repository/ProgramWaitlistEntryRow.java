package com.red_escuela.backend.enrollment.repository;

public interface ProgramWaitlistEntryRow extends WaitlistEntryRow {
    Long getProgramEnrollmentId();

    Long getSubjectId();

    Long getSectionId();

    Long getTermId();
}
