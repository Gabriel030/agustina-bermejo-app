/**
 * <b>Qué es</b>: puerto con el que cada tipo de documento (regular, materias, inscripción a examen y examen) entrega el PDF de una excepción por deuda, con el alcance y el resultado que recibe y devuelve.
 *
 * <p><b>Expone</b>: {@code DebtExceptionDocumentIssuer}, {@code DebtExceptionScope} y {@code DebtExceptionDocument}, para que los emisores de {@code certificate} y {@code procedure} los implementen desde su paquete.
 *
 * <p><b>No hacer</b>: depender de {@code certificate}, {@code procedure} o {@code api}; cada adaptador DEBE re-verificar al renderizar que el alcance (inscripción y registración) pertenece al alumno; el {@code auditDetail} solo admite pares {@code clave=valor} con las claves documentId, code, engineVersion y templateVersion, y nunca montos ni el motivo.
 *
 * <p><b>Ejemplo</b>: {@code DebtExceptionDocumentIssuer}.
 */
package com.red_escuela.backend.graduation.debtexception.port;
