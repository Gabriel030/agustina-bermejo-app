package com.red_escuela.backend.identity.api;

public interface StudentExistenceLookup {

    void requireExistingStudent(Long studentId);
}
