package com.red_escuela.backend.enrollment.usecase;

import com.red_escuela.backend.enrollment.dto.ChangeSectionRequest;
import com.red_escuela.backend.enrollment.dto.SectionEnrollmentResponse;
import com.red_escuela.backend.enrollment.dto.WithdrawEnrollmentRequest;
import com.red_escuela.backend.enrollment.dto.ChangeSectionCandidateResponse;
import com.red_escuela.backend.enrollment.service.ChangeSectionCandidatesFinder;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentService;
import com.red_escuela.backend.enrollment.service.CourseSectionEnrollmentService;
import com.red_escuela.backend.enrollment.service.EnrollmentChange;
import com.red_escuela.backend.enrollment.service.EnrollmentWithdrawal;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.enrollment.service.StaffPersonResolver;
import com.red_escuela.backend.enrollment.service.SubjectSectionEnrollmentService;
import com.red_escuela.backend.shared.UseCase;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@UseCase
public class StaffEnrollmentChangeService implements StaffEnrollmentChange {

    private final SubjectSectionEnrollmentService subjectSectionEnrollmentService;
    private final CourseSectionEnrollmentService courseSectionEnrollmentService;
    private final ProgramEnrollmentService programEnrollmentService;
    private final CourseEnrollmentService courseEnrollmentService;
    private final StaffPersonResolver staffPersonResolver;
    private final ChangeSectionCandidatesFinder changeSectionCandidatesFinder;

    public StaffEnrollmentChangeService(SubjectSectionEnrollmentService subjectSectionEnrollmentService,
            CourseSectionEnrollmentService courseSectionEnrollmentService, ProgramEnrollmentService programEnrollmentService,
            CourseEnrollmentService courseEnrollmentService, StaffPersonResolver staffPersonResolver,
            ChangeSectionCandidatesFinder changeSectionCandidatesFinder) {
        this.changeSectionCandidatesFinder = changeSectionCandidatesFinder;
        this.subjectSectionEnrollmentService = subjectSectionEnrollmentService;
        this.courseSectionEnrollmentService = courseSectionEnrollmentService;
        this.programEnrollmentService = programEnrollmentService;
        this.courseEnrollmentService = courseEnrollmentService;
        this.staffPersonResolver = staffPersonResolver;
    }

    @Override
    public List<ChangeSectionCandidateResponse> listChangeSectionCandidates(Long sectionEnrollmentId) {
        return changeSectionCandidatesFinder.findCandidates(sectionEnrollmentId);
    }

    @Override
    public void withdrawSubject(Long sectionEnrollmentId, String reason) {
        subjectSectionEnrollmentService.withdraw(sectionEnrollmentId, staffChange(reason));
    }

    @Override
    public SectionEnrollmentResponse changeSubjectSection(Long sectionEnrollmentId, ChangeSectionRequest request) {
        return SectionEnrollmentResponse.from(subjectSectionEnrollmentService.changeSection(
                sectionEnrollmentId, request, staffChange(request.reason())));
    }

    @Override
    public void withdrawCourseModule(Long courseSectionEnrollmentId, String reason) {
        courseSectionEnrollmentService.withdraw(courseSectionEnrollmentId, staffChange(reason));
    }

    @Override
    public void withdrawProgram(Long programEnrollmentId, WithdrawEnrollmentRequest request) {
        programEnrollmentService.withdraw(programEnrollmentId, voluntaryWithdrawal(request));
    }

    @Override
    public void withdrawCourse(Long courseEnrollmentId, WithdrawEnrollmentRequest request) {
        courseEnrollmentService.withdraw(courseEnrollmentId, voluntaryWithdrawal(request));
    }

    private EnrollmentChange staffChange(String rawReason) {
        return EnrollmentChange.byStaff(rawReason, staffPersonResolver.currentStaffPersonId());
    }

    private EnrollmentWithdrawal voluntaryWithdrawal(WithdrawEnrollmentRequest request) {
        LocalDate informedDate = Optional.ofNullable(request).map(WithdrawEnrollmentRequest::effectiveDate).orElse(null);
        String reason = Optional.ofNullable(request).map(WithdrawEnrollmentRequest::reason).orElse(null);
        return EnrollmentWithdrawal.voluntaryRegisteredBy(informedDate, staffPersonResolver.currentStaffPersonId(), reason);
    }
}
