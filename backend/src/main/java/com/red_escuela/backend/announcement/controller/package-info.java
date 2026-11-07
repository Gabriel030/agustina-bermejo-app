/**
 * <b>Qué es</b>: coordinación HTTP de los avisos: alta, edición, baja, listados del personal y feed del alumno, y confirmaciones de lectura.
 *
 * <p><b>Expone</b>: los endpoints de avisos del personal y los {@code /me/...} del alumno. Ningún otro módulo lo importa.
 *
 * <p><b>No hacer</b>: llamar a especialistas o repositorios; poner lógica de negocio.
 *
 * <p><b>Ejemplo</b>: {@code AnnouncementController}.
 */
package com.red_escuela.backend.announcement.controller;
