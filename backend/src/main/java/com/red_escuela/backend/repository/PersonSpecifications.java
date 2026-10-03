package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Person;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.util.Set;

public final class PersonSpecifications {

    private PersonSpecifications() {
    }

    public static Specification<Person> withRoleIn(Set<String> roles) {
        return (root, query, cb) -> {
            if (roles == null || roles.isEmpty()) return cb.conjunction();
            var roleJoin = root.join("roles", JoinType.INNER);
            return roleJoin.get("name").in(roles);
        };
    }

    public static Specification<Person> withStatus(StatusType status) {
        return (root, query, cb) ->
                status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static Specification<Person> withSearch(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) return cb.conjunction();
            var pattern = "%" + q.toLowerCase() + "%";
            var unaccentedPattern = cb.function("unaccent", String.class, cb.literal(pattern));
            return cb.or(
                    cb.like(cb.function("unaccent", String.class, cb.lower(root.get("name"))), unaccentedPattern),
                    cb.like(cb.function("unaccent", String.class, cb.lower(root.get("lastname"))), unaccentedPattern),
                    cb.like(cb.lower(root.get("dni")), pattern)
            );
        };
    }
}
