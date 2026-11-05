package com.red_escuela.backend.billing.operation;

import java.time.LocalDate;
import java.util.Objects;

public record CohortPromotionAcquisitionRequest(Long programEnrollmentId, Long courseEnrollmentId, Long programId, Long courseId, Long programCohortId,
                                                LocalDate enrollmentDate, Long actorPersonId) {

    public CohortPromotionAcquisitionRequest {
        if ((programEnrollmentId == null) == (courseEnrollmentId == null)) {
            throw new IllegalArgumentException("An acquisition belongs to a program enrollment or to a course enrollment");
        }
        if ((programEnrollmentId == null) != (courseId != null) || (programEnrollmentId != null) != (programId != null)) {
            throw new IllegalArgumentException("The offering must be of the same kind as the enrollment");
        }
        Objects.requireNonNull(enrollmentDate, "enrollmentDate");
        Objects.requireNonNull(actorPersonId, "actorPersonId");
    }

    public static CohortPromotionAcquisitionRequest forProgramEnrollment(Long programEnrollmentId, Long programId, Long programCohortId, LocalDate enrollmentDate,
                                                                         Long actorPersonId) {
        return new CohortPromotionAcquisitionRequest(programEnrollmentId, null, programId, null, programCohortId, enrollmentDate, actorPersonId);
    }

    public static CohortPromotionAcquisitionRequest forCourseEnrollment(Long courseEnrollmentId, Long courseId, LocalDate enrollmentDate, Long actorPersonId) {
        return new CohortPromotionAcquisitionRequest(null, courseEnrollmentId, null, courseId, null, enrollmentDate, actorPersonId);
    }
}
