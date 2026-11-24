package com.red_escuela.backend.graduation.api;

import com.red_escuela.backend.graduation.entity.CertificateReviewStatus;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ImportedGraduationReviewQuery {
    private static final int CHUNK_SIZE = 1000;

    private final GraduationRequestRepository graduationRequestRepository;

    public ImportedGraduationReviewQuery(GraduationRequestRepository graduationRequestRepository) {
        this.graduationRequestRepository = graduationRequestRepository;
    }

    @Transactional(readOnly = true)
    public long countPendingReview(Collection<Long> graduationRequestIds) {
        List<Long> ids = List.copyOf(graduationRequestIds);
        long total = 0;
        for (int from = 0; from < ids.size(); from += CHUNK_SIZE) {
            List<Long> chunk = ids.subList(from, Math.min(from + CHUNK_SIZE, ids.size()));
            total += graduationRequestRepository.countByIdInAndCertificateReviewStatus(chunk,
                    CertificateReviewStatus.PENDING_REVIEW);
        }
        return total;
    }
}
