package com.red_escuela.backend.billing.authorization.service;

import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequest;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestNotFoundException;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestSearch;
import com.red_escuela.backend.billing.authorization.repository.AuthorizationRequestRepository;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription;
import com.red_escuela.backend.identity.api.PersonNameFinder;
import com.red_escuela.backend.identity.api.PersonRoleNamesFinder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AuthorizationRequestInboxReader {
    private final AuthorizationRequestRepository repository;
    private final AuthorizationRequestSearcher searcher;
    private final AuthorizationCallerAccess callerAccess;
    private final AuthorizationRequestDescriptionAssembler descriptions;
    private final PersonNameFinder personNames;
    private final PersonRoleNamesFinder personRoles;

    public AuthorizationRequestInboxReader(AuthorizationRequestRepository repository, AuthorizationRequestSearcher searcher, AuthorizationCallerAccess callerAccess,
                                           AuthorizationRequestDescriptionAssembler descriptions, PersonNameFinder personNames, PersonRoleNamesFinder personRoles) {
        this.repository = repository;
        this.searcher = searcher;
        this.callerAccess = callerAccess;
        this.descriptions = descriptions;
        this.personNames = personNames;
        this.personRoles = personRoles;
    }

    @Transactional(readOnly = true)
    public InboxPage readPage(AuthorizationRequestSearch search) {
        Page<AuthorizationRequest> found = searcher.search(search, callerAccess.scopeOfCurrentCaller());
        Set<Long> people = new HashSet<>();
        found.getContent().forEach(request -> people.add(request.getRequestedBy().getId()));
        Map<Long, List<String>> roles = personRoles.findRoleNamesByPersonId(people);
        List<InboxEntry> entries = found.getContent().stream()
                .map(request -> new InboxEntry(request, descriptions.describe(request), roles.getOrDefault(request.getRequestedBy().getId(), List.of()))).toList();
        return new InboxPage(entries, personNames.findFullNamesByPersonId(people), search.page(), search.size(), found.getTotalElements());
    }

    @Transactional(readOnly = true)
    public InboxDetail readDetail(Long id) {
        AuthorizationRequest request = repository.findById(id).orElseThrow(() -> new AuthorizationRequestNotFoundException(id));
        callerAccess.requireMayRead(request);
        Set<Long> people = new HashSet<>();
        people.add(request.getRequestedBy().getId());
        if (request.getDecidedBy() != null) {
            people.add(request.getDecidedBy().getId());
        }
        request.getStatusChanges().forEach(change -> {
            if (change.getActorPersonId() != null) {
                people.add(change.getActorPersonId());
            }
        });
        return new InboxDetail(new InboxEntry(request, descriptions.describe(request), personRoles.findRoleNamesByPersonId(List.of(request.getRequestedBy().getId())).getOrDefault(request.getRequestedBy().getId(), List.of())), personNames.findFullNamesByPersonId(people));
    }

    public record InboxEntry(AuthorizationRequest request, AuthorizationRequestDescription description, List<String> requesterRoles) {
    }

    public record InboxPage(List<InboxEntry> entries, Map<Long, String> personNames, int page, int size, long totalElements) {
    }

    public record InboxDetail(InboxEntry entry, Map<Long, String> personNames) {
    }
}
