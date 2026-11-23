/**
 * <b>Qué es</b>: puertos y registros por tipo de trámite del alumno (elegibilidad y trámites aprobados).
 *
 * <p><b>Expone</b>: {@code ProcedureEligibilityStrategy} y {@code ApprovedProcedureSource}, que cada emisor implementa para registrarse.
 *
 * <p><b>No hacer</b>: duplicar la lógica de elegibilidad de un tipo; modificar un registro al sumar un tipo nuevo.
 *
 * <p><b>Ejemplo</b>: {@code RegularApprovedProcedureSource}.
 */
package com.red_escuela.backend.graduation.procedure.specialist;
