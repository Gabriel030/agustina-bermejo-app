/**
 * <b>Qué es</b>: módulo de composición que siembra la institución demo hablando por HTTP con la propia aplicación
 * (mismas rutas y permisos que usa el frontend). Solo se carga con el perfil {@code demo}.
 *
 * <p><b>Expone</b>: nada: no tiene {@code api} ni {@code events}.
 *
 * <p><b>No hacer</b>: acceder a repositorios o entidades de otro módulo (solo usa {@code identity :: api} para completar DNI y rol de los alumnos demo); insertar datos por SQL; cargar un bean fuera del perfil {@code demo}.
 *
 * <p><b>Ejemplo</b>: {@code DemoSeedRunner} ejecuta en orden los {@code DemoSeedStep}, que llaman a la API con {@code DemoHttpClient}.
 * Decisión de fondo: ADR 0002.
 */
@ApplicationModule(displayName = "Demo", allowedDependencies = {"shared", "identity :: api"})
package com.red_escuela.backend.demo;

import org.springframework.modulith.ApplicationModule;
