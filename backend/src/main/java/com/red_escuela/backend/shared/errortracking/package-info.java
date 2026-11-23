/**
 * <b>Qué es</b>: el reporte de errores 5xx a Sentry, inerte mientras {@code SENTRY_DSN} esté vacío, con depuración de datos personales antes de enviar.
 *
 * <p><b>Expone</b>: {@code ServerErrorReporter}, {@code SentryServerErrorReporter}, {@code SentryInitializer} y {@code SentryEventScrubber}.
 *
 * <p><b>No hacer</b>: reportar 4xx ni 409; enviar cuerpos, cookies, cabeceras de autenticación, query strings o usuarios; activar la captura automática de MVC o logback.
 *
 * <p><b>Ejemplo</b>: {@code serverErrorReporter.reportServerError(exception)} desde el handler global de excepciones.
 */
package com.red_escuela.backend.shared.errortracking;
