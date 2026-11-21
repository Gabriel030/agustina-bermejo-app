package com.red_escuela.backend.graduation.certificate.specialist;

import com.red_escuela.backend.enrollment.api.GraduationCandidateFinder;
import com.red_escuela.backend.enrollment.api.GraduationCandidateView;
import com.red_escuela.backend.graduation.certificate.dto.EligibleGraduateResponse;
import com.red_escuela.backend.graduation.certificate.dto.EligibleGraduatesFilter;
import com.red_escuela.backend.graduation.certificate.dto.GraduateEligibilityStatus;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateStatus;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateBlockedByDebtException;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateNotEligibleException;
import com.red_escuela.backend.graduation.certificate.repository.GraduationCertificateRepository;
import com.red_escuela.backend.graduation.certificate.source.GraduationSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class EligibleGraduateLister {

    private record Candidate(GraduationCertificateKind kind, GraduationCandidateView view, GraduateEligibilityStatus status) {
    }

    private final Map<GraduationCertificateKind, GraduationSource> sources = new EnumMap<>(GraduationCertificateKind.class);
    private final GraduationCandidateFinder candidateFinder;
    private final GraduationCertificateRepository repository;

    public EligibleGraduateLister(List<GraduationSource> graduationSources, GraduationCandidateFinder candidateFinder,
                                  GraduationCertificateRepository repository) {
        graduationSources.forEach(source -> sources.put(source.kind(), source));
        this.candidateFinder = candidateFinder;
        this.repository = repository;
    }

    public Page<EligibleGraduateResponse> list(EligibleGraduatesFilter filter, Pageable pageable) {
        List<Candidate> eligible = new ArrayList<>();
        for (GraduationCertificateKind kind : GraduationCertificateKind.values()) {
            if (filter.kind() == null || filter.kind() == kind) {
                eligible.addAll(eligibleOf(kind, filter));
            }
        }
        eligible.sort(Comparator.comparing((Candidate c) -> c.view().fullName())
                .thenComparing(Candidate::kind).thenComparing(c -> c.view().enrollmentId()));
        List<Candidate> filtered = hasDateFilter(filter)
                ? eligible.stream().filter(c -> withinDates(graduationDateOf(c), filter)).toList()
                : eligible;
        int from = (int) Math.min(pageable.getOffset(), filtered.size());
        int to = Math.min(from + pageable.getPageSize(), filtered.size());
        List<EligibleGraduateResponse> content = filtered.subList(from, to).stream().map(this::toResponse).toList();
        return new PageImpl<>(content, pageable, filtered.size());
    }

    private List<Candidate> eligibleOf(GraduationCertificateKind kind, EligibleGraduatesFilter filter) {
        GraduationSource source = sources.get(kind);
        Set<Long> alreadyIssued = kind == GraduationCertificateKind.PROGRAM
                ? repository.findProgramEnrollmentIdsByStatus(GraduationCertificateStatus.VALID)
                : repository.findCourseEnrollmentIdsByStatus(GraduationCertificateStatus.VALID);
        List<GraduationCandidateView> candidates = kind == GraduationCertificateKind.PROGRAM
                ? candidateFinder.findCompletedProgramCandidates(filter.query())
                : candidateFinder.findCourseCandidates(filter.query());
        List<Candidate> result = new ArrayList<>();
        for (GraduationCandidateView view : candidates) {
            if (alreadyIssued.contains(view.enrollmentId())) {
                continue;
            }
            Optional<GraduateEligibilityStatus> status = statusOf(source, view.enrollmentId());
            if (status.isPresent() && (filter.status() == null || filter.status() == status.get())) {
                result.add(new Candidate(kind, view, status.get()));
            }
        }
        return result;
    }

    private Optional<GraduateEligibilityStatus> statusOf(GraduationSource source, Long enrollmentId) {
        try {
            source.requireEligible(enrollmentId);
            return Optional.of(GraduateEligibilityStatus.PENDING);
        } catch (GraduationCertificateBlockedByDebtException e) {
            return Optional.of(GraduateEligibilityStatus.BLOCKED_BY_DEBT);
        } catch (GraduationCertificateNotEligibleException e) {
            return Optional.empty();
        }
    }

    private boolean hasDateFilter(EligibleGraduatesFilter filter) {
        return filter.graduatedFrom() != null || filter.graduatedTo() != null;
    }

    private boolean withinDates(LocalDate date, EligibleGraduatesFilter filter) {
        return (filter.graduatedFrom() == null || !date.isBefore(filter.graduatedFrom()))
                && (filter.graduatedTo() == null || !date.isAfter(filter.graduatedTo()));
    }

    private LocalDate graduationDateOf(Candidate candidate) {
        return sources.get(candidate.kind()).snapshotOf(candidate.view().enrollmentId()).graduationDate();
    }

    private EligibleGraduateResponse toResponse(Candidate candidate) {
        GraduationCandidateView view = candidate.view();
        return new EligibleGraduateResponse(view.enrollmentId(), candidate.kind(), view.studentId(), view.fullName(),
                view.documentTypeLabel(), view.documentNumber(), view.titleName(), graduationDateOf(candidate), candidate.status());
    }
}
