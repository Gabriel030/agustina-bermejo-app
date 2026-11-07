/**
 * <b>Qué es</b>: especialistas de la verificación pública: buscan el certificado por código, enmascaran el documento y limitan la frecuencia de consultas.
 *
 * <p><b>Expone</b>: nada. Los usan los casos de uso y los jobs de este módulo. Se nombran con un sustantivo de rol; {@code Service} queda reservado a un caso de uso.
 *
 * <p><b>No hacer</b>: depender de {@code usecase} o {@code controller}; llamar a otro módulo fuera de su {@code api}.
 *
 * <p><b>Ejemplo</b>: {@code PublicCertificateLookup}.
 */
package com.red_escuela.backend.graduation.certificate.verification.specialist;
