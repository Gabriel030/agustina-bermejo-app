/**
 * <b>Qué es</b>: reglas puras de evaluación, sin Spring ni repositorio; deciden si algo corresponde a partir de los datos que reciben.
 *
 * <p><b>Expone</b>: nada. Las usan los especialistas de este módulo.
 *
 * <p><b>No hacer</b>: depender de Spring, de {@code jakarta.persistence} o de un repositorio; leer el reloj (la fecha llega por parámetro).
 *
 * <p><b>Ejemplo</b>: {@code RegularStudentForFinalExamPolicy}.
 */
package com.red_escuela.backend.evaluation.policy;
