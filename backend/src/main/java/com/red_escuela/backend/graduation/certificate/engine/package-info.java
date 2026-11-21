/**
 * <b>Qué es</b>: motor común de certificados: plantillas, instantánea, código, QR, render del PDF y almacén de archivos.
 *
 * <p><b>Expone</b>: nada fuera del módulo.
 *
 * <p><b>No hacer</b>: conocer los casos de uso ni los controllers; leer repositorios de otro módulo.
 *
 * <p><b>Plantilla y respaldo</b>: los certificados nuevos se renderizan con el diseño A y los textos, logo y firmantes de la versión
 * publicada de la plantilla (conservada en {@code template_version_id}); los emitidos con {@code BUILT_IN_V1} no tienen versión y
 * se regeneran con los textos por defecto y los firmantes de {@code app.certificate.*} guardados en su instantánea.
 *
 * <p><b>Ejemplo</b>: {@code CertificatePdfRenderer}.
 */
package com.red_escuela.backend.graduation.certificate.engine;
