/**
 * <b>Qué es</b>: puertos que los casos de uso de la constancia de materias aprobadas necesitan del resto del sistema.
 *
 * <p><b>Expone</b>: interfaces de actor actual, inscripción propia, datos de elegibilidad, emisión, almacén, auditoría y rechazos.
 *
 * <p><b>No hacer</b>: implementar lógica de negocio ni depender de Spring; las implementaciones viven en {@code specialist}.
 *
 * <p><b>Ejemplo</b>: {@code ApprovedSubjectsCertificateIssuing}.
 */
package com.red_escuela.backend.graduation.certificate.approvedsubjects.port;
