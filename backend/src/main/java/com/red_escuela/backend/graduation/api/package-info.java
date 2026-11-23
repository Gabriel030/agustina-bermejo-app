/**
 * <b>Qué es</b>: superficie pública de graduación. Especialistas concretos (sin interfaz) que otro
 * módulo puede llamar y los records que reciben o devuelven.
 *
 * <p><b>Expone</b>: todo el paquete ({@code @NamedInterface("api")}). Se admite {@code @Transactional}.
 *
 * <p><b>No hacer</b>: depender de {@code usecase} o {@code controller}; recibir o devolver entidades que
 * no estén marcadas {@code @NamedInterface("model")}; publicar eventos ni crear certificados al registrar un egreso histórico.
 *
 * <p><b>Ejemplo</b>: {@code HistoricalGraduationRecorder} guarda un egreso aprobado con su número de registro histórico.
 */
@NamedInterface("api")
package com.red_escuela.backend.graduation.api;

import org.springframework.modulith.NamedInterface;
