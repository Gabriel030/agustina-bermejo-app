/**
 * <b>Qué es</b>: eventos que identity publica para que otros módulos reaccionen: verificación de correo emitida y código de recuperación de contraseña emitido.
 *
 * <p><b>Expone</b>: todo el paquete, como interfaz con nombre {@code events}.
 *
 * <p><b>No hacer</b>: incluir datos sensibles más allá de lo que necesita el receptor; hacer que el publicador espere al receptor.
 *
 * <p><b>Ejemplo</b>: {@code EmailVerificationIssuedEvent}.
 */
@NamedInterface("events")
package com.red_escuela.backend.identity.events;

import org.springframework.modulith.NamedInterface;
