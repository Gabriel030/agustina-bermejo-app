package com.red_escuela.backend.exception;

// Scope-por-carrera: si la materia se comparte entre dos carreras, el alumno tiene que estar
// REGULAR en la cursada de la carrera específica por la que se quiere presentar.
public class StudentNotRegularException extends RuntimeException {
    public StudentNotRegularException(Long enrollmentId) {
        super("La cursada " + enrollmentId + " no está REGULAR, no puede inscribirse a una mesa de examen");
    }
}
