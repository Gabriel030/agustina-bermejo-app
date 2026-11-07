/**
 * <b>Qué es</b>: eventos que graduación publica: solicitud creada, verificada, aprobada, rechazada o bloqueada por deuda, y certificado regular emitido.
 *
 * <p><b>Expone</b>: todo el paquete, como interfaz con nombre {@code events}.
 *
 * <p><b>No hacer</b>: incluir entidades; agregar un campo sin avisar a los listeners de {@code notification}.
 *
 * <p><b>Ejemplo</b>: {@code GraduationRequestApprovedEvent}.
 */
@NamedInterface("events")
package com.red_escuela.backend.graduation.events;

import org.springframework.modulith.NamedInterface;
