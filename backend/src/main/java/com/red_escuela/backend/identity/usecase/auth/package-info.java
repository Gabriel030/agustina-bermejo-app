/**
 * <b>Qué es</b>: los casos de uso de autenticación: iniciar sesión (con correo y con Google), registrarse, renovar y cerrar sesión, y recuperar la contraseña.
 *
 * <p><b>Expone</b>: nada. Lo llaman los controllers del módulo.
 *
 * <p><b>No hacer</b>: llamar a repositorios ni a otro caso de uso; repetir el nombre del módulo en el subpaquete.
 *
 * <p><b>Ejemplo</b>: {@code LoginService} implementa {@code LoginUseCase}.
 */
package com.red_escuela.backend.identity.usecase.auth;
