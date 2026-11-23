/**
 * <b>Qué es</b>: contrato de la importación inicial hacia otros módulos: consulta de las cuentas creadas por importación que todavía no se activaron.
 *
 * <p><b>Expone</b>: todo el paquete, como interfaz con nombre {@code api}: la fachada {@code ImportedAccountsQuery} y sus vistas {@code ImportedAccount} e {@code ImportedAccountsPage}.
 *
 * <p><b>No hacer</b>: exponer entidades o repositorios de la importación; cargar todas las cuentas en memoria (la consulta se pide por tandas con cursor); activar cuentas o enviar correos desde acá.
 *
 * <p><b>Ejemplo</b>: {@code notification} arma una oleada de invitaciones pidiendo tandas de {@code ImportedAccountsQuery.findPendingActivation} y excluye las cuentas sin correo ({@code ImportedAccount.withoutEmail()}).
 */
@NamedInterface("api")
package com.red_escuela.backend.dataimport.api;

import org.springframework.modulith.NamedInterface;
