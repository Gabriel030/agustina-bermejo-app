package com.red_escuela.backend.evaluation.repository;

import java.time.LocalDateTime;

public interface CommissionClosureListRow {

    String getSectionKind();

    Long getSectionId();

    Long getClosureId();

    String getSubjectName();

    String getSectionName();

    Long getTermId();

    String getTermLabel();

    String getInstructorNames();

    Long getStudentCount();

    Long getPromoteCount();

    Long getToReviewCount();

    Long getSnapshotCount();

    String getStatus();

    LocalDateTime getStatusDate();

    String getReportNumber();

    Integer getReportVersion();

    String getBook();

    String getFolio();
}
