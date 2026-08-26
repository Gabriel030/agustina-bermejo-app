package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import org.springframework.data.jpa.domain.Specification;

/**
 * Patrón Specification (táctico, DDD): GET /students filtra por dos criterios
 * independientes y opcionales (status de Person y onboardingStatus). Componer
 * predicados solo cuando el filtro está presente evita explotar en métodos
 * derivados de Spring Data por cada combinación posible.
 */
public final class StudentSpecifications {

    private StudentSpecifications() {
    }

    public static Specification<Student> withPersonStatus(StatusType status) {
        return (root, query, cb) -> {
            if (status == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("person").get("status"), status);
        };
    }

    public static Specification<Student> withOnboardingStatus(StudentOnboardingStatus onboardingStatus) {
        return (root, query, cb) -> {
            if (onboardingStatus == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("onboardingStatus"), onboardingStatus);
        };
    }
}
