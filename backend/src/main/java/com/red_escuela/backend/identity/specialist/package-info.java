/**
 * <b>Qué es</b>: especialistas internos: emiten y rotan tokens, verifican contraseñas, cambian correo y documento, resuelven personas y alumnos actuales, aprovisionan administradores y siembran datos locales. Incluye los adaptadores de puertos y el despachador de correos.
 *
 * <p><b>Expone</b>: nada, salvo los colaboradores {@code CurrentPersonService} y {@code CurrentStudentService}, que las reglas de controller nombran. Los usan los casos de uso y los jobs de este módulo.
 *
 * <p><b>No hacer</b>: depender de {@code usecase} o {@code controller}; llamar a otro módulo fuera de su {@code api}.
 *
 * <p><b>Ejemplo</b>: {@code RefreshTokenRotator}.
 */
package com.red_escuela.backend.identity.specialist;
