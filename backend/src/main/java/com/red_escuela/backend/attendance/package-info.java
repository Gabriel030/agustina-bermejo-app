/**
 * <b>Qué es</b>: módulo de asistencia. Registra la asistencia por clase, las justificaciones y los
 * pedidos de cambio, y calcula el riesgo de quedar libre por inasistencias. Se apoya en
 * {@code identity}, {@code academicstructure} y {@code enrollment}.
 *
 * <p><b>Expone</b>: {@code attendance.api} (especialistas de lectura y de entrega de comprobantes) y
 * {@code attendance.events} (hechos de asistencia). Nada más.
 *
 * <p><b>No hacer</b>: usar desde otro módulo {@code usecase}, {@code service}, {@code repository} o
 * {@code entity} de este; depender de módulos superiores ({@code evaluation}, {@code billing}):
 * se publica un evento o se declara un {@code *Port} en {@code api}.
 *
 * <p><b>Ejemplo</b>: {@code AttendanceController} llama a un caso de uso, que llama a un
 * especialista propio, que llama a un especialista de {@code academicstructure.api}.
 * Decisión de fondo: ADR 0002 y ADR 0007; guía: {@code docs/context/modules.md}.
 */
@ApplicationModule(displayName = "Attendance", allowedDependencies = {"shared", "identity :: *", "academicstructure :: *", "enrollment :: *"})
package com.red_escuela.backend.attendance;

import org.springframework.modulith.ApplicationModule;
