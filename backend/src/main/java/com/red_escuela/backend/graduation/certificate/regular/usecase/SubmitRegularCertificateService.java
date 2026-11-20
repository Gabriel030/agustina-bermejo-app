package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateSubmissionResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.SubmitRegularCertificateRequest;
import com.red_escuela.backend.graduation.certificate.regular.specialist.StudentRegularCertificateSubmitter;
import com.red_escuela.backend.identity.specialist.CurrentStudentService;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class SubmitRegularCertificateService implements SubmitRegularCertificateUseCase {

    private final StudentRegularCertificateSubmitter submitter;
    private final CurrentStudentService currentStudentService;

    public SubmitRegularCertificateService(StudentRegularCertificateSubmitter submitter, CurrentStudentService currentStudentService) {
        this.submitter = submitter;
        this.currentStudentService = currentStudentService;
    }

    @Override
    public RegularCertificateSubmissionResponse submit(SubmitRegularCertificateRequest request) {
        return submitter.submit(request.kind(), request.enrollmentId(), currentStudentService.resolveCurrentStudent().getId());
    }
}
