/**
 * <b>Qué es</b>: snapshots en memoria de los switches y ajustes, con su cargador, y la lectura del archivo de estado de los backups.
 *
 * <p><b>Expone</b>: {@code FeatureSnapshotHolder}, {@code InstituteSettingsSnapshotHolder} y {@code FileBackupStatusSource}.
 *
 * <p><b>No hacer</b>: guardar reglas de negocio; devolver entidades; dejar que un archivo de estado ausente o inválido llegue como error.
 *
 * <p><b>Ejemplo</b>: {@code FeatureSnapshotLoader} recarga el snapshot al cambiar un switch.
 */
package com.red_escuela.backend.platform.infrastructure;
