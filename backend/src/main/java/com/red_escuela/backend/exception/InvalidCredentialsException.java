package com.red_escuela.backend.exception;

/**
 * Usuario inexistente, password incorrecta, cuenta bloqueada o cuenta de otro
 * auth provider (Google) usan todas esta misma excepción, sin distinguir la causa
 * real en el mensaje — evita filtrar qué usernames existen o por qué se rechazó.
 */
public class InvalidCredentialsException extends RuntimeException {
}
