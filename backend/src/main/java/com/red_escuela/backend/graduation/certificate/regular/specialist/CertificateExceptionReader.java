package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionApproval;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionStatus;
import com.red_escuela.backend.graduation.certificate.regular.exception.CertificateExceptionNotFoundException;
import com.red_escuela.backend.graduation.certificate.regular.repository.CertificateExceptionApprovalRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
        Specification<CertificateExceptionApproval> filter = (root, query, builder) -> builder.conjunction();
        if (!seesAll) {
            Long requesterId = currentActorFinder.currentPersonId();
            filter = filter.and((root, query, builder) -> builder.equal(root.get("requestedById"), requesterId));
        }
        if (studentId != null) {
            filter = filter.and((root, query, builder) -> builder.equal(root.get("studentId"), studentId));
        }
        if (status != null) {
            filter = filter.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        return repository.findAll(filter, Sort.by(Sort.Direction.DESC, "id")).stream()
                .map(CertificateExceptionResponses::of)
                .toList();
    }
}
