/**
 * <b>Qué es</b>: módulo de identidad: personas, alumnos, personal, instituciones, sesiones, tokens y verificación de correo.
 *
 * <p><b>Expone</b>: {@code api} (buscadores, creadores y verificadores de personas, alumnos, personal e instituciones, y los puertos de inscripción) y los eventos de {@code events} como interfaces con nombre.
 *
 * <p><b>No hacer</b>: acceder a repositorios o entidades de otro módulo; depender de cualquier módulo salvo {@code shared}.
 *
 * <p><b>Ejemplo</b>: {@code AuthController} llama a {@code LoginUseCase}, que usa especialistas de {@code specialist} y persiste con {@code repository}. Decisión de fondo: ADR 0002 y ADR 0007.
 */
@ApplicationModule(displayName = "Identity", allowedDependencies = {"shared"})
package com.red_escuela.backend.identity;

import org.springframework.modulith.ApplicationModule;
