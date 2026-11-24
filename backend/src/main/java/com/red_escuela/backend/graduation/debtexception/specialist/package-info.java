/**
 * <b>Qué es</b>: especialistas de la excepción por deuda: emiten, listan, vencen, consumen (uso único) y auditan.
 *
 * <p><b>Expone</b>: nada. Los usan los casos de uso y el puerto {@code DebtExceptionGate} del api del módulo.
 *
 * <p><b>No hacer</b>: depender de {@code usecase} o {@code controller}; escribir montos o el motivo en la auditoría.
 *
 * <p><b>Ejemplo</b>: {@code DebtExceptionConsumer}.
 */
package com.red_escuela.backend.graduation.debtexception.specialist;
