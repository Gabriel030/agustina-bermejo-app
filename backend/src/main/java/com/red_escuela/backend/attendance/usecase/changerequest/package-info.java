/**
 * <b>Qué es</b>: los casos de uso de los pedidos de cambio de asistencia: solicitar, listar los propios o por estado, aprobar y rechazar.
 *
 * <p><b>Expone</b>: nada. Lo llaman los controllers del módulo.
 *
 * <p><b>No hacer</b>: llamar a repositorios ni a otro caso de uso; modificar la asistencia sin pasar por el especialista que registra la decisión.
 *
 * <p><b>Ejemplo</b>: {@code RequestAttendanceChangeService} implementa {@code RequestAttendanceChangeUseCase}.
 */
package com.red_escuela.backend.attendance.usecase.changerequest;
