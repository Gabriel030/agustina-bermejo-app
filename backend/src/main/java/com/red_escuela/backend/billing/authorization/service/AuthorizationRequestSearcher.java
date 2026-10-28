package com.red_escuela.backend.billing.authorization.service;

import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequest;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestSearch;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestSort;
import com.red_escuela.backend.billing.authorization.repository.AuthorizationRequestRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AuthorizationRequestSearcher {
    private final AuthorizationRequestRepository repository;

    public AuthorizationRequestSearcher(AuthorizationRequestRepository repository) {
        this.repository = repository;
    }

    public Page<AuthorizationRequest> search(AuthorizationRequestSearch search, AuthorizationCallerAccess.InboxScope scope) {
        return repository.findAll(specificationFor(search, scope), PageRequest.of(search.page(), search.size()));
    }

    private Specification<AuthorizationRequest> specificationFor(AuthorizationRequestSearch search, AuthorizationCallerAccess.InboxScope scope) {
        return (root, query, builder) -> {
            if (!Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                root.fetch("requestedBy");
                query.orderBy(orderingOf(search, root, builder));
            }
            List<Predicate> filters = new ArrayList<>();
            if (scope.onlyRequestedBy() != null) {
                filters.add(builder.equal(root.get("requestedBy").get("id"), scope.onlyRequestedBy()));
            }
            if (search.requestedBy() != null) {
                filters.add(builder.equal(root.get("requestedBy").get("id"), search.requestedBy()));
            }
            if (!search.statuses().isEmpty()) {
                filters.add(root.get("status").in(search.statuses()));
            }
            if (search.filtersOperationTypes()) {
                filters.add(search.matchingOperationTypes().isEmpty() ? builder.disjunction() : root.get("operationType").in(search.matchingOperationTypes()));
            }
            return builder.and(filters.toArray(Predicate[]::new));
        };
    }

    private List<Order> orderingOf(AuthorizationRequestSearch search, jakarta.persistence.criteria.Root<AuthorizationRequest> root, CriteriaBuilder builder) {
        Expression<Object> requestedAt = root.get("requestedAt");
        Expression<Object> id = root.get("id");
        if (search.sort() == AuthorizationRequestSort.REQUESTED_AT) {
            return search.ascending() ? List.of(builder.asc(requestedAt), builder.asc(id)) : List.of(builder.desc(requestedAt), builder.desc(id));
        }
        Expression<Integer> withoutExpiry = builder.<Integer>selectCase().when(builder.isNull(root.get("expiresOn")), 1).otherwise(0);
        Expression<Object> expiresOn = root.get("expiresOn");
        return search.ascending() ? List.of(builder.asc(withoutExpiry), builder.asc(expiresOn), builder.desc(requestedAt), builder.desc(id))
                : List.of(builder.asc(withoutExpiry), builder.desc(expiresOn), builder.desc(requestedAt), builder.desc(id));
    }
}
