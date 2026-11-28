/**
 * <b>Qué es</b>: constancia de materias aprobadas emitida automáticamente (instantánea, hash del PDF, versión del motor y vigencia de 30 días).
 *
 * <p><b>Expone</b>: nada fuera del módulo.
 *
 * <p><b>No hacer</b>: guardar el PDF (es solo caché regenerable); mutar la instantánea ni el hash tras emitir.
 *
 * <p><b>Ejemplo</b>: {@code ApprovedSubjectsCertificate}.
 */
package com.red_escuela.backend.graduation.certificate.approvedsubjects.entity;
