package com.red_escuela.backend.billing.authorization.service;

import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequest;
import com.red_escuela.backend.billing.authorization.domain.PendingInvoiceAuthorization;
import com.red_escuela.backend.billing.authorization.repository.AuthorizationRequestRepository;
import com.red_escuela.backend.identity.api.PersonNameLookup;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PendingInvoiceAuthorizationReader {
    private final AuthorizationRequestRepository requests;
    private final PersonNameLookup personNames;

    public PendingInvoiceAuthorizationReader(AuthorizationRequestRepository requests, PersonNameLookup personNames) {
        this.requests = requests;
        this.personNames = personNames;
    }

    @Transactional(readOnly = true)
    public Map<Long, List<PendingInvoiceAuthorization>> findPendingByInvoiceIdNewestFirst(Collection<Long> invoiceIds) {
        if (invoiceIds.isEmpty()) {
            return Map.of();
        }
        List<AuthorizationRequestRepository.InvoiceRequestLink> links = requests.findPendingRequestLinksOfInvoices(invoiceIds);
        if (links.isEmpty()) {
            return Map.of();
        }
        Map<Long, AuthorizationRequest> requestById = requests.findAllById(links.stream().map(AuthorizationRequestRepository.InvoiceRequestLink::getRequestId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(AuthorizationRequest::getId, Function.identity()));
        Set<Long> requesterIds = requestById.values().stream().map(request -> request.getRequestedBy().getId()).collect(Collectors.toSet());
        Map<Long, String> names = personNames.findFullNamesByPersonId(requesterIds);
        Map<Long, List<PendingInvoiceAuthorization>> pendingByInvoice = new HashMap<>();
        links.stream().sorted(Comparator.comparing(AuthorizationRequestRepository.InvoiceRequestLink::getRequestId).reversed()).forEach(link -> {
            AuthorizationRequest request = requestById.get(link.getRequestId());
            Long requesterId = request.getRequestedBy().getId();
            pendingByInvoice.computeIfAbsent(link.getInvoiceId(), invoiceId -> new ArrayList<>())
                    .add(new PendingInvoiceAuthorization(request.getId(), request.getOperationType(), requesterId, names.get(requesterId), request.getRequestedAt()));
        });
        return pendingByInvoice;
    }
}
