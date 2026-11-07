/**
 * <b>Qué es</b>: módulo de avisos a comisiones y materias: el personal los crea, programa y segmenta por audiencia (asistencia, notas, trabajos prácticos); el alumno los recibe y confirma lectura.
 *
 * <p><b>Expone</b>: los eventos de {@code events} ({@code AnnouncementPublishedEvent}, {@code AnnouncementUpdatedEvent}, {@code AnnouncementDeletedEvent}) como interfaz con nombre {@code events}. No tiene {@code api}.
 *
 * <p><b>No hacer</b>: acceder a repositorios o entidades de otro módulo; notificar desde acá: se publica un evento y {@code notification} lo recibe.
 *
 * <p><b>Ejemplo</b>: {@code AnnouncementController} llama a un caso de uso de {@code usecase/broadcast}, que usa un especialista de {@code specialist}, que persiste con {@code repository} y publica un evento. Decisión de fondo: ADR 0002 y ADR 0007.
 */
@ApplicationModule(displayName = "Announcement", allowedDependencies = {"shared", "identity :: *", "academicstructure :: *", "enrollment :: *", "attendance :: *", "evaluation :: *", "billing :: *", "graduation :: *"})
package com.red_escuela.backend.announcement;

import org.springframework.modulith.ApplicationModule;
