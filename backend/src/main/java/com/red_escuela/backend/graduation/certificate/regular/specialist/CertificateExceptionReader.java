package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionApproval;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionStatus;
import com.red_escuela.backend.graduation.certificate.regular.exception.CertificateExceptionNotFoundException;
import com.red_escuela.backend.graduation.certificate.regular.repository.CertificateExceptionApprovalRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CertificateExceptionReader {

    private final CertificateExceptionApprovalRepository repository;
    private final CurrentActorFinder currentActorFinder;

    public CertificateExceptionReader(CertificateExceptionApprovalRepository repository, CurrentActorFinder currentActorFinder) {
        this.repository = repository;
        this.currentActorFinder = currentActorFinder;
    }

    @Transactional(readOnly = true)
    public CertificateExceptionResponse get(Long exceptionId, boolean seesAll) {
        CertificateExceptionApproval approval = repository.findById(exceptionId).orElseThrow(CertificateExceptionNotFoundException::new);
        if (!seesAll && !approval.getRequestedById().equals(currentActorFinder.currentPersonId())) {
            throw new CertificateExceptionNotFoundException();
        }
        return CertificateExceptionResponses.of(approval);
    }

    @Transactional(readOnly = true)
    public List<CertificateExceptionResponse> list(boolean seesAll, Long studentId, CertificateExceptionStatus status) {
        List<CertificateExceptionApproval> candidates = seesAll
                ? repository.findAllByOrderByIdDesc()
                : repository.findAllByRequestedByIdOrderByIdDesc(currentActorFinder.currentPersonId());
        return candidates.stream()
                .filter(approval -> studentId == null || approval.getStudentId().equals(studentId))
                .filter(approval -> status == null || approval.getStatus() == status)
                .map(CertificateExceptionResponses::of)
                .toList();
    }
}
