/**
 * <b>Qué es</b>: especialistas de la configuración de emisión de documentos: leen y actualizan la fila única con bloqueo optimista y auditoría.
 *
 * <p><b>Expone</b>: nada. Los usan los casos de uso de este módulo y el puerto {@code DocumentIssuanceSettings}. Se nombran con un sustantivo de rol; {@code Service} queda reservado a un caso de uso.
 *
 * <p><b>No hacer</b>: depender de {@code usecase} o {@code controller}; llamar a otro módulo fuera de su {@code api}.
 *
 * <p><b>Ejemplo</b>: {@code DocumentIssuanceSettingsUpdater}.
 */
package com.red_escuela.backend.graduation.documentsettings.specialist;
