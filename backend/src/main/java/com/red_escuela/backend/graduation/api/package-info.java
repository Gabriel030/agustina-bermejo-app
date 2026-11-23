/**
 * <b>Qué es</b>: superficie pública de graduación. Especialistas concretos (sin interfaz) que otro
 * módulo puede llamar y los records que reciben o devuelven.
 *
 * <p><b>Expone</b>: todo el paquete ({@code @NamedInterface("api")}). Se admite {@code @Transactional}. Incluye el puerto {@code DebtExceptionDocumentIssuer} que implementa cada tipo de documento para descargar su PDF por excepción de deuda.
 *
 * <p><b>No hacer</b>: depender de {@code usecase} o {@code controller}; recibir o devolver entidades que
 * no estén marcadas {@code @NamedInterface("model")}; publicar eventos ni crear certificados al registrar un egreso histórico. Cada adaptador de {@code DebtExceptionDocumentIssuer} DEBE re-verificar al renderizar que el alcance (inscripción y registración) pertenece al alumno, sin confiar en la validación al emitir. El {@code auditDetail} que devuelve solo admite pares {@code clave=valor} con las claves documentId, code, engineVersion y templateVersion, y nunca montos ni el motivo; el resto hace fallar la descarga y la revierte.
 *
 * <p><b>Ejemplo</b>: {@code HistoricalGraduationRecorder} guarda un egreso aprobado con su número de registro histórico.
 */
@NamedInterface("api")
package com.red_escuela.backend.graduation.api;

import org.springframework.modulith.NamedInterface;
