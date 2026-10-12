package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.Locale;

public final class StudentSpecifications {

    private StudentSpecifications() {
    }

    public static Specification<Student> withPersonStatus(StatusType status) {
        return (root, query, cb) -> {
            if (status == null) return cb.conjunction();
            return cb.equal(root.get("person").get("status"), status);
        };
    }

    public static Specification<Student> withOnboardingStatus(StudentOnboardingStatus onboardingStatus) {
        return (root, query, cb) -> {
            if (onboardingStatus == null) return cb.conjunction();
            return cb.equal(root.get("onboardingStatus"), onboardingStatus);
        };
    }

    public static Specification<Student> withRole(String roleName) {
        return (root, query, cb) -> {
            var subquery = query.subquery(Long.class);
            var person = subquery.from(Person.class);
            var role = person.join("roles");
            subquery.select(person.get("id"))
                    .where(cb.equal(person.get("id"), root.get("person").get("id")),
                            cb.equal(role.get("name"), roleName));
            return cb.exists(subquery);
        };
    }

    public static Specification<Student> withSearch(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) return cb.conjunction();
            Join<Student, Person> person = root.join("person", JoinType.INNER);
            Predicate[] everyTermMatches = Arrays.stream(q.trim().split("\\s+"))
                    .map(term -> matchesAnyField(cb, person, term))
                    .toArray(Predicate[]::new);
            return cb.and(everyTermMatches);
        };
    }

    private static Predicate matchesAnyField(CriteriaBuilder cb, Join<Student, Person> person, String term) {
        String lowered = "%" + term.toLowerCase(Locale.ROOT) + "%";
        Expression<String> pattern = cb.function("unaccent", String.class, cb.literal(lowered));
        return cb.or(
                cb.like(cb.function("unaccent", String.class, cb.lower(person.get("name"))), pattern),
                cb.like(cb.function("unaccent", String.class, cb.lower(person.get("lastname"))), pattern),
                cb.like(cb.lower(person.get("dni")), cb.literal(lowered)),
                cb.like(cb.lower(person.get("file")), cb.literal(lowered)));
    }
}
