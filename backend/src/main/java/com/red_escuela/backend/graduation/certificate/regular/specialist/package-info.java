/**
 * <b>Qué es</b>: especialistas del certificado de alumno regular: evalúan la regularidad, resuelven la situación de pagos, emiten, leen, entregan el PDF y vencen los certificados. Incluye el job de vencimiento.
 *
 * <p><b>Expone</b>: nada. Los usan los casos de uso y los jobs de este módulo. Se nombran con un sustantivo de rol; {@code Service} queda reservado a un caso de uso.
 *
 * <p><b>No hacer</b>: depender de {@code usecase} o {@code controller}; llamar a otro módulo fuera de su {@code api}.
 *
 * <p><b>Ejemplo</b>: {@code RegularCertificateIssuer}.
 */
package com.red_escuela.backend.graduation.certificate.regular.specialist;
