/**
 * <b>Qué es</b>: guarda de módulos: bloquea los endpoints de un módulo apagado ({@code @RequiresModule}) con interceptor y aspecto.
 *
 * <p><b>Expone</b>: {@code ModuleGuard} y sus componentes de configuración.
 *
 * <p><b>No hacer</b>: consultar el estado del módulo por fuera de {@code ModuleGuard}.
 *
 * <p><b>Ejemplo</b>: un endpoint con {@code @RequiresModule} responde apagado si el switch está en OFF.
 */
package com.red_escuela.backend.platform.guard;
