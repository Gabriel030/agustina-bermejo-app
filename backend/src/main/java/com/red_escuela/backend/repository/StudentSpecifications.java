package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import org.springframework.data.jpa.domain.Specification;

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

    public static Specification<Student> withSearch(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) return cb.conjunction();
            var person = root.join("person", jakarta.persistence.criteria.JoinType.INNER);
            var pattern = cb.function("unaccent", String.class,
                    cb.literal("%" + q.toLowerCase() + "%"));
            return cb.or(
                    cb.like(cb.function("unaccent", String.class, cb.lower(person.get("name"))), pattern),
                    cb.like(cb.function("unaccent", String.class, cb.lower(person.get("lastname"))), pattern),
                    cb.like(cb.lower(person.get("dni")), cb.literal("%" + q.toLowerCase() + "%"))
            );
        };
    }
}
