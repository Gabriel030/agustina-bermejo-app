package com.red_escuela.backend.notification;

import com.red_escuela.backend.enrollment.events.CourseWaitlistExpiredEvent;
import com.red_escuela.backend.enrollment.events.DirectorApprovalRequestApprovedEvent;
import com.red_escuela.backend.enrollment.events.DirectorApprovalRequestRejectedEvent;
import com.red_escuela.backend.enrollment.events.EnrollmentFeeExemptionGrantedEvent;
import com.red_escuela.backend.enrollment.events.EnrollmentFeeExemptionRevokedEvent;
import com.red_escuela.backend.identity.entity.Person;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnrollmentEventsNotificationListenerTest {

    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final EnrollmentEventsNotificationListener listener = new EnrollmentEventsNotificationListener(publisher, entityManager);
    private final Person student = personWithId(10L);
    private final Person requester = personWithId(20L);

    @BeforeEach
    void stubPersonReferences() {
        when(entityManager.getReference(eq(Person.class), eq(10L))).thenReturn(student);
        when(entityManager.getReference(eq(Person.class), eq(20L))).thenReturn(requester);
    }

    private static Person personWithId(Long id) {
        Person person = new Person();
        person.setId(id);
        return person;
    }

    private DomainNotificationEvent published() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(captor.capture());
        return (DomainNotificationEvent) captor.getValue();
    }

    private static PersonRecipient recipientOf(Person person) {
        return new PersonRecipient(person);
    }

    @Test
    void waitlistExpirationNotifiesTheStudentWithTheCourseName() {
        listener.notifyWaitlistExpiration(new CourseWaitlistExpiredEvent("c", Instant.EPOCH, 3L, 10L, "Jazz I"));

        DomainNotificationEvent notification = published();
        assertThat(notification.message()).isEqualTo("No conseguiste lugar en Jazz I"
                + " antes del cierre de la lista de espera — tu inscripción se canceló y el pago se reembolsará.");
        assertThat(notification.recipients()).containsExactly(recipientOf(student));
        assertThat(notification.relatedEntityType()).isEqualTo("CourseEnrollment");
        assertThat(notification.relatedEntityId()).isEqualTo(3L);
    }

    @Test
    void directorApprovalAppendsNotesAndNotifiesStudentAndRequester() {
        listener.notifyDirectorApproval(new DirectorApprovalRequestApprovedEvent("c", Instant.EPOCH, 7L, "Equivalencia de Ana Paz", "Todo en orden", 10L, 20L));

        DomainNotificationEvent notification = published();
        assertThat(notification.message()).isEqualTo("Dirección aprobó el pedido de Equivalencia de Ana Paz. Notas: Todo en orden");
        assertThat(notification.recipients()).containsExactly(recipientOf(student), recipientOf(requester));
        assertThat(notification.relatedEntityType()).isEqualTo("DirectorApprovalRequest");
        assertThat(notification.relatedEntityId()).isEqualTo(7L);
    }

    @Test
    void directorRejectionWithoutNotesNotifiesOnlyTheStudentWhenTheyRequestedIt() {
        listener.notifyDirectorRejection(new DirectorApprovalRequestRejectedEvent("c", Instant.EPOCH, 8L, "Equivalencia de Ana Paz", null, 10L, 10L));

        DomainNotificationEvent notification = published();
        assertThat(notification.message()).isEqualTo("Dirección rechazó el pedido de Equivalencia de Ana Paz.");
        assertThat(notification.recipients()).containsExactly(recipientOf(student));
    }

    @Test
    void directorRejectionWithNotesAndNoRequesterLabelsTheReason() {
        listener.notifyDirectorRejection(new DirectorApprovalRequestRejectedEvent("c", Instant.EPOCH, 8L, "Baja", "Falta documentación", 10L, null));

        DomainNotificationEvent notification = published();
        assertThat(notification.message()).isEqualTo("Dirección rechazó el pedido de Baja. Motivo: Falta documentación");
        assertThat(notification.recipients()).containsExactly(recipientOf(student));
    }

    @Test
    void feeExemptionGrantNotifiesTheStudentAgainstTheProgramEnrollment() {
        listener.notifyFeeExemptionGranted(new EnrollmentFeeExemptionGrantedEvent("c", Instant.EPOCH, 5L, 10L));

        DomainNotificationEvent notification = published();
        assertThat(notification.message()).isEqualTo(
                "La institución te habilitó para inscribirte sin tener la matrícula paga. La matrícula sigue pendiente de pago.");
        assertThat(notification.recipients()).containsExactly(recipientOf(student));
        assertThat(notification.relatedEntityType()).isEqualTo("ProgramEnrollment");
        assertThat(notification.relatedEntityId()).isEqualTo(5L);
    }

    @Test
    void feeExemptionRevocationNotifiesTheStudentAgainstTheProgramEnrollment() {
        listener.notifyFeeExemptionRevoked(new EnrollmentFeeExemptionRevokedEvent("c", Instant.EPOCH, 5L, 10L));

        DomainNotificationEvent notification = published();
        assertThat(notification.message()).isEqualTo(
                "Se revocó tu habilitación para inscribirte sin matrícula. Pagá la matrícula para poder inscribirte.");
        assertThat(notification.recipients()).containsExactly(recipientOf(student));
        assertThat(notification.relatedEntityId()).isEqualTo(5L);
    }
}
