/**
 * <b>Qué es</b>: el reporte de errores 5xx a Sentry, inerte mientras {@code SENTRY_DSN} esté vacío, con depuración de datos personales antes de enviar.
 *
 * <p><b>Expone</b>: {@code ServerErrorReporter}, {@code SentryServerErrorReporter}, {@code SentryInitializer}, {@code SentryEventScrubber} y el túnel {@code POST /public/monitoring/tunnel} ({@code SentryTunnelController}, {@code SentryTunnel}).
 *
 * <p><b>No hacer</b>: reportar 4xx ni 409; enviar cuerpos, cookies, cabeceras de autenticación, query strings o usuarios; activar la captura automática de MVC o logback; tomar el host destino del túnel de algo que no sea el DSN configurado; seguir redirecciones del upstream.
 *
 * <p><b>Ejemplo</b>: {@code serverErrorReporter.reportServerError(exception)} desde el handler global de excepciones.
 */
package com.red_escuela.backend.shared.errortracking;
