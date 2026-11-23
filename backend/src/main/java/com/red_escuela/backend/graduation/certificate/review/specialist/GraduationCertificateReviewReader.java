package com.red_escuela.backend.graduation.certificate.review.specialist;

import com.red_escuela.backend.graduation.certificate.review.dto.GraduationCertificateReviewResponse;
import com.red_escuela.backend.graduation.entity.CertificateReviewStatus;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.exception.GraduationRequestNotFoundException;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import com.red_escuela.backend.identity.entity.Person;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class GraduationCertificateReviewReader {

    private final GraduationRequestRepository graduationRequestRepository;

    public GraduationCertificateReviewReader(GraduationRequestRepository graduationRequestRepository) {
        this.graduationRequestRepository = graduationRequestRepository;
    }

    public Page<GraduationCertificateReviewResponse> list(CertificateReviewStatus status, String query, Pageable pageable) {
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return graduationRequestRepository.findCertificateReviews(status, likePattern(query), unsorted).map(this::toResponse);
    }

    public GraduationCertificateReviewResponse get(Long graduationRequestId) {
        return graduationRequestRepository.findWithReviewDetailsById(graduationRequestId)
                .map(this::toResponse)
                .orElseThrow(() -> new GraduationRequestNotFoundException(graduationRequestId));
    }

    private GraduationCertificateReviewResponse toResponse(GraduationRequest request) {
        var enrollment = request.getProgramEnrollment();
        var student = enrollment.getStudent();
        Person person = student.getPerson();
        Person reviewer = request.getCertificateReviewedBy();
        return new GraduationCertificateReviewResponse(request.getId(), enrollment.getId(), student.getId(),
                (person.getLastname() + ", " + person.getName()).toUpperCase(Locale.ROOT), person.getDni(),
                enrollment.getProgram().getName(), request.getRegistryNumber(), request.getLegacyTitleNumber(),
                request.getCertificateReviewStatus(), reviewer == null ? null : reviewer.getId(),
                reviewer == null ? null : reviewer.getName() + " " + reviewer.getLastname(), request.getCertificateReviewedAt());
    }

    private String likePattern(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String escaped = query.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
