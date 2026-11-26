package com.red_escuela.backend.graduation.certificate.signatory.specialist;

import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryDetailResponse;
import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryFilter;
import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatorySummaryResponse;
import com.red_escuela.backend.graduation.certificate.signatory.dto.SignatorySignatureContent;
import com.red_escuela.backend.graduation.certificate.signatory.dto.SignatoryTemplateUsageResponse;
import com.red_escuela.backend.graduation.certificate.signatory.entity.CertificateSignatory;
import com.red_escuela.backend.graduation.certificate.signatory.entity.CertificateSignatorySignature;
import com.red_escuela.backend.graduation.certificate.signatory.exception.SignatoryNotFoundException;
import com.red_escuela.backend.graduation.certificate.signatory.repository.CertificateSignatoryRepository;
import com.red_escuela.backend.graduation.certificate.signatory.repository.CertificateSignatorySignatureRepository;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateSignatoryUsages;
import com.red_escuela.backend.shared.persistence.LikePatterns;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CertificateSignatoryReader {

    private final CertificateSignatoryRepository repository;
    private final CertificateSignatorySignatureRepository signatureRepository;
    private final CertificateSignatoryFinder finder;
    private final CertificateTemplateSignatoryUsages usages;

    public CertificateSignatoryReader(CertificateSignatoryRepository repository, CertificateSignatorySignatureRepository signatureRepository,
                                      CertificateSignatoryFinder finder, CertificateTemplateSignatoryUsages usages) {
        this.repository = repository;
        this.signatureRepository = signatureRepository;
        this.finder = finder;
        this.usages = usages;
    }

    public Page<CertificateSignatorySummaryResponse> list(CertificateSignatoryFilter filter, Pageable pageable) {
        Pageable sorted = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("name").and(Sort.by("id")));
        Page<CertificateSignatory> page = repository.findAll(matching(filter), sorted);
        List<Long> ids = page.getContent().stream().map(CertificateSignatory::getId).toList();
        Map<Long, CertificateSignatorySignature> signatures = signatureRepository.findBySignatoryIdIn(ids).stream()
                .collect(Collectors.toMap(CertificateSignatorySignature::getSignatoryId, Function.identity()));
        Map<Long, List<SignatoryTemplateUsageResponse>> used = usages.usagesOf(ids);
        return page.map(signatory -> CertificateSignatoryResponses.summary(signatory, signatures.get(signatory.getId()),
                used.getOrDefault(signatory.getId(), List.of())));
    }

    public CertificateSignatoryDetailResponse get(Long id) {
        return detailOf(finder.find(id));
    }

    public SignatorySignatureContent signature(Long id) {
        finder.find(id);
        CertificateSignatorySignature signature = signatureRepository.findBySignatoryId(id).orElseThrow(SignatoryNotFoundException::new);
        return new SignatorySignatureContent(signature.getContent(), signature.getContentType());
    }

    CertificateSignatoryDetailResponse detailOf(CertificateSignatory signatory) {
        CertificateSignatorySignature signature = signatureRepository.findBySignatoryId(signatory.getId()).orElse(null);
        return CertificateSignatoryResponses.detail(signatory, signature,
                usages.usagesOf(List.of(signatory.getId())).getOrDefault(signatory.getId(), List.of()));
    }

    private Specification<CertificateSignatory> matching(CertificateSignatoryFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.active() != null) {
                predicates.add(builder.equal(root.get("active"), filter.active()));
            }
            if (filter.query() != null && !filter.query().isBlank()) {
                String like = LikePatterns.contains(filter.query().strip().toLowerCase());
                predicates.add(builder.or(builder.like(builder.lower(root.get("name")), like, LikePatterns.ESCAPE_CHARACTER),
                        builder.like(builder.lower(root.get("role")), like, LikePatterns.ESCAPE_CHARACTER)));
            }
            if (filter.documentType() != null) {
                predicates.add(builder.isMember(filter.documentType(), root.get("allowedDocumentTypes")));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
