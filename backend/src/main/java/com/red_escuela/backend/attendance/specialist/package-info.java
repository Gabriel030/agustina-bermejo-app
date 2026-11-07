/**
 * <b>Qué es</b>: especialistas internos de asistencia; cada uno hace una parte concreta de un flujo
 * (armar un padrón, calcular el riesgo, registrar una justificación).
 *
 * <p><b>Expone</b>: nada. Los usan los casos de uso de este módulo; si otro módulo los necesita, van a
 * {@code api}. Se nombran con un sustantivo de rol ({@code Finder}, {@code Reader}, {@code Recorder},
 * {@code Calculator}, {@code Resolver}...); {@code Service} queda reservado a un caso de uso.
 *
 * <p><b>No hacer</b>: depender de {@code usecase} o {@code controller}; llamar a otro módulo fuera de su
 * {@code api}; dejar reglas puras acá (van en {@code policy}).
 *
 * <p><b>Ejemplo</b>: {@code AttendanceRiskCalculator}. Acá vive {@code @Transactional}.
 */
package com.red_escuela.backend.attendance.specialist;
