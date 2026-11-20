/**
 * <b>Qué es</b>: casos de uso del estado de los backups que escriben los scripts de despliegue.
 *
 * <p><b>Expone</b>: interfaces {@code XxxUseCase} con su implementación {@code XxxService}.
 *
 * <p><b>No hacer</b>: llamar a otro caso de uso; leer el archivo de estado directo sin un especialista.
 *
 * <p><b>Ejemplo</b>: {@code GetBackupStatusService} devuelve el estado para el Director o la plataforma.
 */
package com.red_escuela.backend.platform.usecase.backup;
