/**
 * <b>Qué es</b>: contrato de evaluación hacia otros módulos: buscadores de notas, mesas de examen y esquemas, vistas y el puerto {@code GraduationRequestTriggerPort}.
 *
 * <p><b>Expone</b>: todo el paquete, como interfaz con nombre {@code api}.
 *
 * <p><b>No hacer</b>: exponer entidades o repositorios; agregar un método que otro módulo pueda resolver con un evento.
 *
 * <p><b>Ejemplo</b>: {@code GradeItemFinder}.
 */
@NamedInterface("api")
package com.red_escuela.backend.evaluation.api;

import org.springframework.modulith.NamedInterface;
