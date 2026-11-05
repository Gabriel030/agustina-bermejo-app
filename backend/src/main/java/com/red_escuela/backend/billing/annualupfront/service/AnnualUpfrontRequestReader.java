package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequest;
import com.red_escuela.backend.billing.annualupfront.exception.AnnualUpfrontEnrollmentNotFoundException;
import com.red_escuela.backend.billing.annualupfront.exception.AnnualUpfrontRequestNotFoundException;
import com.red_escuela.backend.billing.annualupfront.repository.AnnualUpfrontRequestRepository;
import com.red_escuela.backend.billing.configuration.service.BillingActorResolver;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentFactsFinder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AnnualUpfrontRequestReader {
    static final int MAX_PAGE_SIZE = 100;

    private final AnnualUpfrontRequestRepository requests;
    private final AnnualUpfrontRequestViewAssembler viewAssembler;
    private final AnnualUpfrontQuoteService quoteService;
    private final BillingEnrollmentFactsFinder enrollmentLookup;
    private final BillingActorResolver actorResolver;

    public AnnualUpfrontRequestReader(AnnualUpfrontRequestRepository requests, AnnualUpfrontRequestViewAssembler viewAssembler, AnnualUpfrontQuoteService quoteService,
                                      BillingEnrollmentFactsFinder enrollmentLookup, BillingActorResolver actorResolver) {
        this.requests = requests;
        this.viewAssembler = viewAssembler;
        this.quoteService = quoteService;
        this.enrollmentLookup = enrollmentLookup;
        this.actorResolver = actorResolver;
    }

    @Transactional(readOnly = true)
    public AnnualUpfrontRequestView findRequestOfCurrentStudent(Long requestId) {
        AnnualUpfrontRequest request = findRequestOrThrow(requestId);
        try {
            quoteService.requireEnrollmentOfCurrentStudent(request.getProgramEnrollmentId());
        } catch (AnnualUpfrontEnrollmentNotFoundException notTheStudentsEnrollment) {
            throw new AnnualUpfrontRequestNotFoundException(requestId);
        }
        return viewAssembler.assembleViewOf(request);
    }

    @Transactional(readOnly = true)
    public AnnualUpfrontRequestView findRequestForStaff(Long requestId) {
        return viewAssembler.assembleViewOf(findRequestOrThrow(requestId));
    }

    @Transactional(readOnly = true)
    public List<AnnualUpfrontRequestView> listRequestsOfCurrentStudent() {
        return requests.findByRequestedByPersonIdOrderByIdDesc(actorResolver.currentPersonId()).stream().map(viewAssembler::assembleViewOf).toList();
    }

    @Transactional(readOnly = true)
    public List<AnnualUpfrontRequestView> listRequestsOfEnrollmentOfCurrentStudent(Long programEnrollmentId) {
        quoteService.requireEnrollmentOfCurrentStudent(programEnrollmentId);
        return listRequestsOfEnrollment(programEnrollmentId);
    }

    @Transactional(readOnly = true)
    public List<AnnualUpfrontRequestView> listRequestsOfEnrollmentForStaff(Long programEnrollmentId) {
        enrollmentLookup.findProgramEnrollmentFacts(programEnrollmentId).orElseThrow(() -> new AnnualUpfrontEnrollmentNotFoundException(programEnrollmentId));
        return listRequestsOfEnrollment(programEnrollmentId);
    }

    @Transactional(readOnly = true)
    public AnnualUpfrontRequestPage searchRequests(AnnualUpfrontRequestSearch search, int page, int size) {
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<AnnualUpfrontRequest> found = requests.searchByStatusAndLectiveYear(search.status() == null ? null : search.status().name(), search.lectiveYear(),
                PageRequest.of(boundedPage, boundedSize));
        return new AnnualUpfrontRequestPage(found.getContent().stream().map(viewAssembler::assembleViewOf).toList(), boundedPage, boundedSize, found.getTotalElements());
    }

    private List<AnnualUpfrontRequestView> listRequestsOfEnrollment(Long programEnrollmentId) {
        return requests.findByProgramEnrollmentIdOrderByIdDesc(programEnrollmentId).stream().map(viewAssembler::assembleViewOf).toList();
    }

    private AnnualUpfrontRequest findRequestOrThrow(Long requestId) {
        return requests.findById(requestId).orElseThrow(() -> new AnnualUpfrontRequestNotFoundException(requestId));
    }
}
