/**
 * <b>Qué es</b>: hechos que asistencia publica para que otros módulos reaccionen sin acoplarse.
 *
 * <p><b>Expone</b>: todo el paquete ({@code @NamedInterface("events")}).
 *
 * <p><b>No hacer</b>: llevar entidades JPA (solo ids y valores planos); nombrar el evento en presente
 * o como orden: se nombra el hecho en pasado.
 *
 * <p><b>Ejemplo</b>: {@code AttendanceStatusChangedEvent}.
 */
@NamedInterface("events")
package com.red_escuela.backend.attendance.events;

import org.springframework.modulith.NamedInterface;
