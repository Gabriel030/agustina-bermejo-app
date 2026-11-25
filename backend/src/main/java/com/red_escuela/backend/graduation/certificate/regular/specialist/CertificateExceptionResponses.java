package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.enrollment.api.EnrollmentNameFinder;
import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionApproval;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.identity.api.PersonNameFinder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CertificateExceptionResponses {

    private final PersonNameFinder personNameFinder;
    private final EnrollmentNameFinder enrollmentNameFinder;

    public CertificateExceptionResponses(PersonNameFinder personNameFinder, EnrollmentNameFinder enrollmentNameFinder) {
        this.personNameFinder = personNameFinder;
        this.enrollmentNameFinder = enrollmentNameFinder;
    }

    public CertificateExceptionResponse of(CertificateExceptionApproval approval) {
        return all(List.of(approval)).get(0);
    }

    public List<CertificateExceptionResponse> all(Collection<CertificateExceptionApproval> approvals) {
        Map<Long, String> students = personNameFinder.findFullNamesByStudentId(
                approvals.stream().map(CertificateExceptionApproval::getStudentId).distinct().toList());
        Set<Long> personIds = new HashSet<>();
        approvals.forEach(approval -> {
            personIds.add(approval.getRequestedById());
            if (approval.getDecidedById() != null) {
                personIds.add(approval.getDecidedById());
            }
        });
        Map<Long, String> people = personNameFinder.findFullNamesByPersonId(personIds);
        Map<Long, String> programs = enrollmentNameFinder.findProgramNamesByProgramEnrollmentId(enrollmentIdsOf(approvals, RegularityKind.PROGRAM));
        Map<Long, String> courses = enrollmentNameFinder.findCourseNamesByCourseEnrollmentId(enrollmentIdsOf(approvals, RegularityKind.COURSE));
        return approvals.stream().map(approval -> build(approval, students.get(approval.getStudentId()),
                (approval.getKind() == RegularityKind.PROGRAM ? programs : courses).get(approval.enrollmentId()),
                people.get(approval.getRequestedById()), approval.getDecidedById() == null ? null : people.get(approval.getDecidedById()))).toList();
    }

    private List<Long> enrollmentIdsOf(Collection<CertificateExceptionApproval> approvals, RegularityKind kind) {
        return approvals.stream().filter(approval -> approval.getKind() == kind).map(CertificateExceptionApproval::enrollmentId).distinct().toList();
    }

    private CertificateExceptionResponse build(CertificateExceptionApproval approval, String studentName, String programName,
                                               String requestedByName, String decidedByName) {
        return new CertificateExceptionResponse(approval.getId(), approval.getStudentId(), approval.getKind(), approval.enrollmentId(),
                approval.getOrganismKind(), approval.getOrganismReference(), approval.getReason(), approval.getStatus(),
                approval.getRequestedById(), approval.getRequestedAt(), approval.getDecidedById(), approval.getDecidedAt(),
                approval.getDecisionReason(), approval.getValidUntil(), approval.isCreatedByDirector(),
                approval.getDebtAtRequest(), approval.getOverdueInstallmentsAtRequest(),
                studentName, programName, requestedByName, decidedByName);
    }
}
