/**
 * <b>Qué es</b>: módulo de graduación: solicitudes de graduación, verificación de deuda y elegibilidad, y certificados de graduación y de alumno regular con verificación pública.
 *
 * <p><b>Expone</b>: los eventos de {@code events} como interfaz con nombre {@code events}. No tiene {@code api}.
 *
 * <p><b>No hacer</b>: acceder a repositorios o entidades de otro módulo; notificar desde acá: se publica un evento y {@code notification} lo recibe.
 *
 * <p><b>Ejemplo</b>: {@code GraduationRequestController} llama a un caso de uso de {@code usecase/request}, que usa un especialista de {@code specialist}, que persiste con {@code repository} y publica un evento. Decisión de fondo: ADR 0002 y ADR 0007.
 */
@ApplicationModule(displayName = "Graduation", allowedDependencies = {"shared", "identity :: *", "academicstructure :: *", "enrollment :: *", "attendance :: *", "evaluation :: *", "billing :: *", "platform :: *"})
package com.red_escuela.backend.graduation;

import org.springframework.modulith.ApplicationModule;
