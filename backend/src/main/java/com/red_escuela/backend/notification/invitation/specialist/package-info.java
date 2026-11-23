/**
 * <b>Qué es</b>: compositores transaccionales y colaboradores de la oleada: escaneo de candidatos, creación, ciclo de vida y lectura.
 *
 * <p><b>Expone</b>: {@code CreateInvitationWaveTransaction}, {@code InvitationWaveLifecycleTransaction}, {@code InvitationWaveReader}, {@code InvitationWaveCandidateScanner}, {@code InvitationWaveSettings}.
 *
 * <p><b>No hacer</b>: depender de {@code usecase}; usar repositorios o entidades de otro módulo; enviar correos (eso lo hace el despacho).
 *
 * <p><b>Ejemplo</b>: {@code InvitationWaveLifecycleTransaction.startInvitationWave} bloquea la oleada, valida la transición y registra {@code startedAt}.
 */
package com.red_escuela.backend.notification.invitation.specialist;
