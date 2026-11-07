/**
 * <b>Qué es</b>: especialistas internos de la vista del alumno. Cada uno lee de la {@code api} de otro
 * módulo una parte del registro.
 *
 * <p><b>Expone</b>: nada. Los usan los casos de uso de este módulo. Se nombran con un sustantivo de rol
 * ({@code Finder}, {@code Reader}, {@code Recorder}...); {@code Service} queda reservado a un caso de uso.
 *
 * <p><b>No hacer</b>: depender de {@code usecase} o {@code controller}; llamar a otro módulo fuera de su {@code api}.
 *
 * <p><b>Ejemplo</b>: {@code AcademicRecordGradeItemFinder} o {@code AcademicRecordEnrollmentOwnershipChecker}.
 */
package com.red_escuela.backend.academicrecord.specialist;
