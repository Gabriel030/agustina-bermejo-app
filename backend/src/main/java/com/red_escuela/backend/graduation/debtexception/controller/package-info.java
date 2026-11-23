/**
 * <b>Qué es</b>: endpoints de excepciones por deuda para Administrativo y Director; el Subdirector solo entra si el permiso se le delega en la grilla. Quien tenga a la vez el rol real SUBDIRECTOR y el rol real ADMINISTRATIVO queda regido solo por SUBDIRECTOR: denegado sin grant y permitido con grant (falla cerrado).
 *
 * <p><b>Expone</b>: rutas HTTP que delegan en casos de uso.
 *
 * <p><b>No hacer</b>: tocar repositorios o servicios de otros módulos.
 *
 * <p><b>Ejemplo</b>: {@code DocumentDebtExceptionController}.
 */
package com.red_escuela.backend.graduation.debtexception.controller;
