/**
 * <b>Qué es</b>: módulo de composición de la importación inicial de datos del instituto: lotes, archivos CSV,
 * filas en preparación, validación en ensayo (sin escribir en las tablas de negocio) y carga por tandas.
 * Está en la capa de {@code academicrecord}: puede usar la {@code api} de los demás módulos.
 *
 * <p><b>Expone</b>: nada: no tiene {@code api} ni {@code events}.
 *
 * <p><b>No hacer</b>: acceder a repositorios o entidades de otro módulo; llamar a casos de uso vivos de otro módulo
 * ni publicar eventos al cargar historia (cada módulo expone un especialista {@code Historical*Recorder} en su
 * {@code api}); importar facturación; dar acceso o enviar correos a las personas cargadas.
 *
 * <p><b>Ejemplo</b>: {@code DataImportController} llama a {@code CommitImportBatchUseCase}, que reparte las filas
 * válidas en tandas de 200 a {@code CommitImportChunkTransaction}; esta abre la transacción y llama a la definición
 * del bloque (por ejemplo {@code PersonImportDefinition}), que escribe con {@code HistoricalPersonRecorder} de {@code identity}.
 * Spec: Importación inicial de datos del instituto (Épica 29, bloque D).
 */
@ApplicationModule(displayName = "Data import", allowedDependencies = {"shared", "identity :: *", "platform :: *"})
package com.red_escuela.backend.dataimport;

import org.springframework.modulith.ApplicationModule;
