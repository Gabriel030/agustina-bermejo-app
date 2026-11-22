/**
 * <b>Qué es</b>: los hechos que este módulo publica cuando un aviso se publica, se edita o se borra.
 *
 * <p><b>Expone</b>: los eventos {@code AnnouncementPublishedEvent}, {@code AnnouncementUpdatedEvent} y {@code AnnouncementDeletedEvent}, con la anotación {@code @NamedInterface("events")}. Los consume {@code notification}.
 *
 * <p><b>No hacer</b>: poner entidades dentro de un evento; hacer que el evento cambie estado.
 *
 * <p><b>Ejemplo</b>: {@code AnnouncementPublishedEvent}.
 */
@NamedInterface("events")
package com.red_escuela.backend.announcement.events;

import org.springframework.modulith.NamedInterface;
