/**
 * <b>Qué es</b>: reglas puras que deciden si un email corresponde (opt-out previo al vencimiento, email verificado).
 *
 * <p><b>Expone</b>: {@code XxxPolicy} sin Spring ni reloj.
 *
 * <p><b>No hacer</b>: usar Spring, repositorios o {@code Clock}.
 *
 * <p><b>Ejemplo</b>: {@code VerifiedEmailRequiredPolicy}.
 */
package com.red_escuela.backend.notification.delivery.policy;
