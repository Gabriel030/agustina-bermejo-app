/**
 * <b>Qué es</b>: modelo persistente de asistencia (herramienta de los especialistas).
 *
 * <p><b>Expone</b>: solo las entidades marcadas {@code @NamedInterface("model")}; el resto es interno.
 *
 * <p><b>No hacer</b>: usarlas en un DTO o en un evento; setters que rompan invariantes.
 *
 * <p><b>Ejemplo</b>: {@code ClassSession}.
 */
package com.red_escuela.backend.attendance.entity;
