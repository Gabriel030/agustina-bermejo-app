/**
 * <b>Qué es</b>: especialistas de los certificados de graduación: emiten el certificado y entregan su PDF, con huella y URL de verificación.
 *
 * <p><b>Expone</b>: nada. Los usan los casos de uso y los jobs de este módulo. Se nombran con un sustantivo de rol; {@code Service} queda reservado a un caso de uso.
 *
 * <p><b>No hacer</b>: depender de {@code usecase} o {@code controller}; llamar a otro módulo fuera de su {@code api}.
 *
 * <p><b>Ejemplo</b>: {@code GraduationCertificateIssuer}.
 */
package com.red_escuela.backend.graduation.certificate.specialist;
