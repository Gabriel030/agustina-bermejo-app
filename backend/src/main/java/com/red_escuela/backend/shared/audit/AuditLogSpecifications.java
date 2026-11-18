package com.red_escuela.backend.shared.audit;

import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    public static Specification<AuditLogEntry> matching(AuditLogFilter filter) {
        return (root, query, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (!filter.actions().isEmpty()) {
                predicates.add(root.get("action").in(filter.actions()));
            }
            if (filter.entityType() != null) {
                predicates.add(builder.equal(root.get("entityType"), filter.entityType()));
            }
            if (filter.entityId() != null) {
                predicates.add(builder.equal(root.get("entityId"), filter.entityId()));
            }
            if (filter.actorPersonId() != null) {
                predicates.add(builder.equal(root.get("actorPersonId"), filter.actorPersonId()));
            }
            if (!filter.actorRoles().isEmpty()) {
                predicates.add(root.get("actorRole").in(filter.actorRoles()));
            }
            if (filter.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), filter.from().atStartOfDay()));
            }
            if (filter.to() != null) {
                predicates.add(builder.lessThan(root.get("occurredAt"), filter.to().plusDays(1).atStartOfDay()));
            }
            return builder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
