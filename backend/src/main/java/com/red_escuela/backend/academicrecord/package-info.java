/**
 * <b>Qué es</b>: módulo de la vista del alumno sobre su propio recorrido (horario, asistencia, notas,
 * inscripciones a comisiones). No tiene entidades ni repositorios: compone lo que exponen los demás
 * módulos. Es de las capas más altas: puede depender de casi todos.
 *
 * <p><b>Expone</b>: nada: no tiene {@code api} ni {@code events}.
 *
 * <p><b>No hacer</b>: acceder a repositorios o entidades de otro módulo; agregar un flujo que no sea una
 * lectura o una acción del propio alumno sobre su registro.
 *
 * <p><b>Ejemplo</b>: {@code MeAcademicRecordController} llama a un caso de uso de {@code usecase},
 * que llama a un especialista de {@code service} (hoy) que lee la {@code api} de {@code attendance} o {@code evaluation}.
 * Decisión de fondo: ADR 0002 y ADR 0007.
 */
@ApplicationModule(displayName = "Academic record", allowedDependencies = {"shared", "identity :: *", "academicstructure :: *", "enrollment :: *", "attendance :: *", "evaluation :: *", "billing :: *", "graduation :: *", "announcement :: *", "notification :: *"})
package com.red_escuela.backend.academicrecord;

import org.springframework.modulith.ApplicationModule;
