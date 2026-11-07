/**
 * <b>Qué es</b>: especialistas internos: despachan avisos, reparten destinatarios, registran lecturas y verifican la pertenencia del docente a la sección. Incluye el job de avisos programados.
 *
 * <p><b>Expone</b>: nada. Los usan los casos de uso y el job de este módulo. Se nombran con un sustantivo de rol; {@code Service} queda reservado a un caso de uso.
 *
 * <p><b>No hacer</b>: depender de {@code usecase} o {@code controller}; llamar a otro módulo fuera de su {@code api}.
 *
 * <p><b>Ejemplo</b>: {@code AnnouncementDispatcher}.
 */
package com.red_escuela.backend.announcement.specialist;
