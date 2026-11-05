package com.red_escuela.backend.billing.discount.repository;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountSearch;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class StudentDiscountSpecifications {

    private StudentDiscountSpecifications() {
    }

    public static Specification<StudentDiscount> matching(StudentDiscountSearch search) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search.status() != null) {
                predicates.add(builder.equal(root.get("status"), search.status()));
            }
            if (search.type() != null) {
                predicates.add(builder.equal(root.get("discountType"), search.type()));
            }
            if (search.discountDefinitionId() != null) {
                predicates.add(builder.equal(root.get("discountDefinitionId"), search.discountDefinitionId()));
            }
            if (search.studentId() != null) {
                predicates.add(builder.equal(root.get("studentId"), search.studentId()));
            }
            if (search.programEnrollmentId() != null) {
                predicates.add(builder.equal(root.get("programEnrollmentId"), search.programEnrollmentId()));
            }
            if (search.courseEnrollmentId() != null) {
                predicates.add(builder.equal(root.get("courseEnrollmentId"), search.courseEnrollmentId()));
            }
            if (search.definitionType() != null) {
                Subquery<Long> definitions = query.subquery(Long.class);
                Root<DiscountDefinition> definition = definitions.from(DiscountDefinition.class);
                definitions.select(definition.get("id")).where(builder.equal(definition.get("discountType"), search.definitionType()));
                predicates.add(root.get("discountDefinitionId").in(definitions));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
