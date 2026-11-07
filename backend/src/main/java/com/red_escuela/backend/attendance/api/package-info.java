/**
 * <b>Qué es</b>: superficie pública de asistencia. Especialistas concretos (sin interfaz) que otro
 * módulo puede llamar y los records {@code *View} que devuelven. Es el mismo rol que
 * {@code service} (hoy) o {@code specialist} (destino), con otra visibilidad.
 *
 * <p><b>Expone</b>: todo el paquete ({@code @NamedInterface("api")}). Se admite {@code @Transactional}.
 *
 * <p><b>No hacer</b>: depender de {@code usecase} o {@code controller}; recibir o devolver entidades que
 * no estén marcadas {@code @NamedInterface("model")}; declarar interfaces salvo un puerto invertido
 * {@code *Port} que implemente otro módulo; llamar hacia módulos superiores.
 *
 * <p><b>Ejemplo</b>: {@code StudentAttendanceReader} devuelve un {@code StudentAttendanceView}.
 */
@NamedInterface("api")
package com.red_escuela.backend.attendance.api;

import org.springframework.modulith.NamedInterface;
