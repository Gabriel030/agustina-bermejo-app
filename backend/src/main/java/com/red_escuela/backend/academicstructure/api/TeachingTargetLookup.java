package com.red_escuela.backend.academicstructure.api;

public interface TeachingTargetLookup {
    boolean programExists(Long programId);

    boolean courseExists(Long courseId);
}
