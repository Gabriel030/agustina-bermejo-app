/**
 * <b>Qué es</b>: módulo de evaluación: ítems de nota, publicación y corrección, mesas de examen con sus inscripciones y tribunal, esquemas y configuración de evaluación por comisión.
 *
 * <p><b>Expone</b>: {@code api} (buscadores de notas, mesas y esquemas, y el puerto {@code GraduationRequestTriggerPort}) y los eventos de {@code events} como interfaces con nombre.
 *
 * <p><b>No hacer</b>: acceder a repositorios o entidades de otro módulo; notificar desde acá: se publica un evento y {@code notification} lo recibe.
 *
 * <p><b>Ejemplo</b>: {@code GradePublicationController} llama a un caso de uso de {@code usecase/gradepublication}, que usa un especialista de {@code specialist}, que persiste con {@code repository} y publica un evento. Decisión de fondo: ADR 0002 y ADR 0007.
 */
@ApplicationModule(displayName = "Evaluation", allowedDependencies = {"shared", "identity :: *", "academicstructure :: *", "enrollment :: *", "attendance :: *"})
package com.red_escuela.backend.evaluation;

import org.springframework.modulith.ApplicationModule;
