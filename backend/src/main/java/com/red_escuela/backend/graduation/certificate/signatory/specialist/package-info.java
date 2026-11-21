/**
 * <b>Qué es</b>: especialistas de los firmantes centralizados de certificados: lectura, alta y edición con auditoría, firma propia y resolución para las plantillas.
 *
 * <p><b>Expone</b>: {@code CertificateSignatoryResolver}, que usan las plantillas para validar y copiar el firmante elegido.
 *
 * <p><b>No hacer</b>: guardar la imagen de la firma en la auditoría; depender de {@code usecase}.
 *
 * <p><b>Ejemplo</b>: {@code CertificateSignatoryWriter}.
 */
package com.red_escuela.backend.graduation.certificate.signatory.specialist;
