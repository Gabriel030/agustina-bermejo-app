/**
 * <b>Qué es</b>: el guion de cada flujo que pide un actor. Cada caso de uso es un par
 * {@code XxxUseCase} (interfaz de un único método) y {@code XxxService} (su implementación, con
 * {@code @UseCase}), más su {@code XxxCommand} cuando recibe dos o más parámetros.
 *
 * <p><b>Expone</b>: nada. Es interno del módulo (no lleva {@code @NamedInterface}).
 *
 * <p><b>No hacer</b>: llamar a otro caso de uso; abrir {@code @Transactional} (vive en el especialista);
 * tocar repositorios directamente; llamar a un especialista no público de otro módulo.
 *
 * <p><b>Ejemplo</b>: {@code JustifyAttendanceService} implementa {@code JustifyAttendanceUseCase}.
 */
package com.red_escuela.backend.attendance.usecase;
