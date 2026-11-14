package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.dto.LegalAcceptanceResponse;
import com.red_escuela.backend.identity.entity.LegalDocumentType;
import com.red_escuela.backend.identity.repository.LegalAcceptanceRepository;
import com.red_escuela.backend.identity.repository.LegalAcceptanceRow;
import com.red_escuela.backend.shared.pagination.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LegalAcceptanceFinder {

    private final LegalAcceptanceRepository legalAcceptanceRepository;

    public LegalAcceptanceFinder(LegalAcceptanceRepository legalAcceptanceRepository) {
        this.legalAcceptanceRepository = legalAcceptanceRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<LegalAcceptanceResponse> search(Long personId, String documentType, String version,
                                                        int page, int size) {
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), PageResponse.MAX_SIZE);
        Page<LegalAcceptanceRow> result = legalAcceptanceRepository.search(personId, documentType == null || documentType.isBlank() ? null : LegalDocumentType.valueOf(documentType),
                version == null || version.isBlank() ? null : version.trim(), PageRequest.of(boundedPage, boundedSize));
        return new PageResponse<>(result.getContent().stream().map(LegalAcceptanceFinder::toResponse).toList(), boundedPage, boundedSize, result.getTotalElements());
    }

    private static LegalAcceptanceResponse toResponse(LegalAcceptanceRow row) {
        return new LegalAcceptanceResponse(row.getId(), row.getPersonId(), row.getPersonName(), row.getPersonLastname(),
                row.getDocumentType(), row.getVersion(), row.getAcceptedAt());
    }
}
