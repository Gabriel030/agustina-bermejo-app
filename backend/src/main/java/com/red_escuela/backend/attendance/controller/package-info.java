/**
 * <b>Qué es</b>: coordinación HTTP de asistencia. Recibe el pedido, valida el DTO y delega en un caso de uso.
 *
 * <p><b>Expone</b>: los endpoints REST. Ningún otro módulo lo importa.
 *
 * <p><b>No hacer</b>: llamar a especialistas, repositorios o casos de uso de otro módulo; poner lógica de negocio.
 *
 * <p><b>Ejemplo</b>: {@code AttendanceController}.
 */
package com.red_escuela.backend.attendance.controller;
