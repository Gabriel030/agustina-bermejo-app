/**
 * <b>Qué es</b>: especialistas internos: cargan, publican y corrigen notas, gestionan mesas de examen y sus inscripciones, resuelven la configuración de evaluación de cada comisión y arman la grilla del docente. Incluye el job de recordatorio de configuraciones y el adaptador de {@code SectionEvaluationSetupPort}.
 *
 * <p><b>Expone</b>: nada. Los usan los casos de uso y los jobs de este módulo. Se nombran con un sustantivo de rol; {@code Service} queda reservado a un caso de uso.
 *
 * <p><b>No hacer</b>: depender de {@code usecase} o {@code controller}; llamar a otro módulo fuera de su {@code api}.
 *
 * <p><b>Ejemplo</b>: {@code SectionGradeItemPublisher}.
 */
package com.red_escuela.backend.evaluation.specialist;
