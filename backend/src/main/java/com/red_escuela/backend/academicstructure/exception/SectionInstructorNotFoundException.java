package com.red_escuela.backend.academicstructure.exception;

/**
 * También se usa cuando la asignación existe pero pertenece a OTRA comisión distinta
 * de la que indica la URL — desde afuera es indistinguible de "no existe", y es la
 * semántica REST correcta (no confirmar por otra vía que el id existe en otro lado).
 */
public class SectionInstructorNotFoundException extends RuntimeException {
    public SectionInstructorNotFoundException(Long id) {
        super("Asignación de docente no encontrada en esa comisión: " + id);
    }
}
