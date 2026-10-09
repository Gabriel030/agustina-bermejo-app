package com.red_escuela.backend.shared.pricing;

import java.util.Objects;

public record OfferingReference(Long programId, Long courseId) {

    public OfferingReference {
        if ((programId == null) == (courseId == null)) {
            throw new IllegalArgumentException("An offering is either a program or a course");
        }
    }

    public static OfferingReference ofProgram(Long programId) {
        return new OfferingReference(Objects.requireNonNull(programId, "programId"), null);
    }

    public static OfferingReference ofCourse(Long courseId) {
        return new OfferingReference(null, Objects.requireNonNull(courseId, "courseId"));
    }

    public boolean belongsToProgram() {
        return programId != null;
    }
}
