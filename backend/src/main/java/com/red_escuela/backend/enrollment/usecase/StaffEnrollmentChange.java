package com.red_escuela.backend.enrollment.usecase;

import com.red_escuela.backend.enrollment.dto.ChangeSectionRequest;
import com.red_escuela.backend.enrollment.dto.SectionEnrollmentResponse;
import com.red_escuela.backend.enrollment.dto.WithdrawEnrollmentRequest;

public interface StaffEnrollmentChange {

    java.util.List<com.red_escuela.backend.enrollment.dto.ChangeSectionCandidateResponse> listChangeSectionCandidates(Long sectionEnrollmentId);

    void withdrawSubject(Long sectionEnrollmentId, String reason);

    SectionEnrollmentResponse changeSubjectSection(Long sectionEnrollmentId, ChangeSectionRequest request);

    void withdrawCourseModule(Long courseSectionEnrollmentId, String reason);

    void withdrawProgram(Long programEnrollmentId, WithdrawEnrollmentRequest request);

    void withdrawCourse(Long courseEnrollmentId, WithdrawEnrollmentRequest request);
}
