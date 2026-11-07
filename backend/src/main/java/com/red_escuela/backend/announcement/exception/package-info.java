/**
 * <b>Qué es</b>: los errores de negocio de los avisos (audiencia vacía, fecha en el pasado, aviso ya enviado, no ser instructor de la sección).
 *
 * <p><b>Expone</b>: excepciones de dominio que el manejador global traduce a HTTP.
 *
 * <p><b>No hacer</b>: lanzar excepciones genéricas; usar estas para otro módulo.
 *
 * <p><b>Ejemplo</b>: {@code AnnouncementNotFoundException}.
 */
package com.red_escuela.backend.announcement.exception;
