/**
 * <b>Qué es</b>: módulo de notificaciones: recibe los eventos de los demás módulos y entrega avisos in-app y por email, con planificación de envíos y métricas de recordatorios.
 *
 * <p><b>Expone</b>: nada: es el módulo más alto, nadie lo consume.
 *
 * <p><b>No hacer</b>: que otro módulo dependa de él; acceder a repositorios o entidades de otro módulo: se usa su {@code api}.
 *
 * <p><b>Ejemplo</b>: un listener de {@code specialist} recibe un evento de billing, publica una {@code XxxNotification} del {@code domain} y {@code NotificationDispatcher} la entrega por cada canal. Decisión de fondo: ADR 0002 y ADR 0007.
 */
@ApplicationModule(displayName = "Notification", allowedDependencies = {"shared", "identity :: *", "academicstructure :: *", "enrollment :: *", "attendance :: *", "evaluation :: *", "billing :: *", "graduation :: *", "announcement :: *", "platform :: api"})
package com.red_escuela.backend.notification;

import org.springframework.modulith.ApplicationModule;
