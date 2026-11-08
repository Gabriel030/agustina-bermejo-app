/**
 * <b>Qué es</b>: eventos que evaluación publica: notas publicadas y corregidas, mesas canceladas o reprogramadas, y el ciclo de la configuración de evaluación.
 *
 * <p><b>Expone</b>: todo el paquete, como interfaz con nombre {@code events}.
 *
 * <p><b>No hacer</b>: incluir entidades; agregar un campo sin avisar a los listeners de {@code notification}.
 *
 * <p><b>Ejemplo</b>: {@code GradesPublishedEvent}.
 */
@NamedInterface("events")
package com.red_escuela.backend.evaluation.events;

import org.springframework.modulith.NamedInterface;
