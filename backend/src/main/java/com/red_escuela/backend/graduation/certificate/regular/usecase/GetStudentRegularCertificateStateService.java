package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.StudentRegularCertificateStateResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.specialist.StudentRegularCertificateStateReader;
import com.red_escuela.backend.identity.specialist.CurrentStudentService;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetStudentRegularCertificateStateService implements GetStudentRegularCertificateStateUseCase {

    private final StudentRegularCertificateStateReader reader;
    private final CurrentStudentService currentStudentService;

    public GetStudentRegularCertificateStateService(StudentRegularCertificateStateReader reader, CurrentStudentService currentStudentService) {
        this.reader = reader;
        this.currentStudentService = currentStudentService;
    }

    @Override
    public StudentRegularCertificateStateResponse stateOf(RegularityKind kind, Long enrollmentId) {
        return reader.stateOf(kind, enrollmentId, currentStudentService.resolveCurrentStudent().getId());
    }
}
