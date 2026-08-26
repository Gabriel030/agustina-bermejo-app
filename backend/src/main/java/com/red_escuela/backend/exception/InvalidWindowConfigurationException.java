package com.red_escuela.backend.exception;

// Distinta de AdmissionWindowClosedException/EnrollmentWindowClosedException: acá la ventana
// no está simplemente cerrada, está mal cargada (fecha de inicio posterior a la de fin), lo
// que sin este chequeo bloquearía la inscripción para todos permanentemente sin ningún indicio
// de que la causa es un error de configuración y no "todavía no abrió" / "ya cerró".
public class InvalidWindowConfigurationException extends RuntimeException {
    public InvalidWindowConfigurationException(String message) {
        super(message);
    }
}
