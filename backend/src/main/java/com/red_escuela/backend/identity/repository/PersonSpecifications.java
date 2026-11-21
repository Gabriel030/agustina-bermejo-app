package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.shared.persistence.LikePatterns;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Person;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;
import java.util.Set;

public final class PersonSpecifications {

    private PersonSpecifications() {
    }

    public static Specification<Person> withRoleIn(Set<String> roles) {
        return (root, query, cb) -> {
            if (roles == null || roles.isEmpty()) return cb.disjunction();
            var roleJoin = root.join("roles", JoinType.INNER);
            return roleJoin.get("name").in(roles);
        };
    }

    public static Specification<Person> withStatus(StatusType status) {
        return (root, query, cb) ->
                status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static Specification<Person> withFullNameContaining(String term) {
        return (root, query, cb) -> {
            var pattern = LikePatterns.contains(term.toLowerCase(Locale.ROOT));
            var name = cb.coalesce(root.<String>get("name"), "");
            var lastname = cb.coalesce(root.<String>get("lastname"), "");
            var fullName = cb.lower(cb.concat(cb.concat(name, " "), lastname));
            return cb.like(cb.function("unaccent", String.class, fullName),
                    cb.function("unaccent", String.class, cb.literal(pattern)), LikePatterns.ESCAPE_CHARACTER);
        };
    }

    public static Specification<Person> withRoleNamed(String roleName) {
        return (root, query, cb) -> cb.exists(roleSubquery(root, query, cb, roleName));
    }

    public static Specification<Person> withoutRoleNamed(String roleName) {
        return (root, query, cb) -> cb.not(cb.exists(roleSubquery(root, query, cb, roleName)));
    }

    private static Subquery<Long> roleSubquery(Root<Person> root, CriteriaQuery<?> query, CriteriaBuilder cb, String roleName) {
        Subquery<Long> subquery = query.subquery(Long.class);
        Root<Person> holder = subquery.from(Person.class);
        var role = holder.join("roles", JoinType.INNER);
        subquery.select(holder.get("id")).where(cb.equal(holder.get("id"), root.get("id")), cb.equal(role.get("name"), roleName));
        return subquery;
    }

    public static Specification<Person> withSearch(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) return cb.conjunction();
            var pattern = LikePatterns.contains(q.toLowerCase());
            var unaccentedPattern = cb.function("unaccent", String.class, cb.literal(pattern));
            return cb.or(
                    cb.like(cb.function("unaccent", String.class, cb.lower(root.get("name"))), unaccentedPattern, LikePatterns.ESCAPE_CHARACTER),
                    cb.like(cb.function("unaccent", String.class, cb.lower(root.get("lastname"))), unaccentedPattern, LikePatterns.ESCAPE_CHARACTER),
                    cb.like(cb.lower(root.get("dni")), pattern, LikePatterns.ESCAPE_CHARACTER)
            );
        };
    }
}
