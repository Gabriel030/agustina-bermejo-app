/**
 * <b>Qué es</b>: repositorios Spring Data de la oleada y de sus destinatarios.
 *
 * <p><b>Expone</b>: {@code InvitationWaveRepository}, {@code InvitationWaveRecipientRepository}.
 *
 * <p><b>No hacer</b>: usarlos desde otro módulo o desde controllers.
 *
 * <p><b>Ejemplo</b>: {@code InvitationWaveRepository.findByIdForUpdate} bloquea la fila antes de cambiar de estado}.
 */
package com.red_escuela.backend.notification.invitation.repository;
