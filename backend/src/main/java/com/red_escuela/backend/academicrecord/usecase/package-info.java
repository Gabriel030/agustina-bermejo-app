/**
 * <b>Qué es</b>: el guion de cada flujo del alumno sobre su registro. Cada caso de uso es un par
 * {@code XxxUseCase} y {@code XxxService} (su implementación, con {@code @UseCase}). Hoy están en el
 * subpaquete {@code studentrecord}, que se aplana en la migración (ADR 0007).
 *
 * <p><b>Expone</b>: nada. Es interno del módulo.
 *
 * <p><b>No hacer</b>: llamar a otro caso de uso; abrir {@code @Transactional}; tocar repositorios.
 *
 * <p><b>Ejemplo</b>: un {@code XxxService} que implementa su {@code XxxUseCase}.
 */
package com.red_escuela.backend.academicrecord.usecase;
