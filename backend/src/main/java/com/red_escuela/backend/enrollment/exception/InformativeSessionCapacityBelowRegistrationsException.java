package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class InformativeSessionCapacityBelowRegistrationsException extends UnprocessableEntityException {
    public InformativeSessionCapacityBelowRegistrationsException(Long sessionId, int capacity, long registrations) {
        super("La capacidad " + capacity + " de la charla informativa " + sessionId + " es menor a las " + registrations + " reservas existentes");
    }
}
