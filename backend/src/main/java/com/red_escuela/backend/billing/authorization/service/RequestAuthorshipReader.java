package com.red_escuela.backend.billing.authorization.service;

import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequest;
import com.red_escuela.backend.billing.authorization.domain.RequestAuthorship;
import com.red_escuela.backend.billing.authorization.repository.AuthorizationRequestRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RequestAuthorshipReader {
    private final AuthorizationRequestRepository requests;

    public RequestAuthorshipReader(AuthorizationRequestRepository requests) {
        this.requests = requests;
    }

    @Transactional(readOnly = true)
    public Map<Long, RequestAuthorship> findAuthorshipByRequestId(Collection<Long> requestIds) {
        return requests.findAllById(requestIds).stream()
                .collect(Collectors.toMap(AuthorizationRequest::getId, request -> new RequestAuthorship(request.getRequestedBy().getId(),
                        request.getDecidedBy() == null ? null : request.getDecidedBy().getId(), request.getDecidedAt())));
    }
}
