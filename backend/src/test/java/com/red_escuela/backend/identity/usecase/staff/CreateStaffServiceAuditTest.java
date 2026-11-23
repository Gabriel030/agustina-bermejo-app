package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.identity.dto.CreateStaffRequest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.exception.DniAlreadyExistsException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.service.CreateStaffTransaction;
import com.red_escuela.backend.identity.specialist.IdentityAuditRecorder;
import com.red_escuela.backend.identity.specialist.PasswordResetCodeRequester;
import com.red_escuela.backend.identity.specialist.PersonFactory;
import com.red_escuela.backend.identity.specialist.StaffInstructorProfileCreator;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static com.red_escuela.backend.identity.usecase.staff.StaffTestFixtures.policyActingAs;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CreateStaffServiceAuditTest {

    private static final Long NEW_ID = 91L;

    private final PersonRepository personRepository = mock(PersonRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final PersonFactory personFactory = mock(PersonFactory.class);
    private final AuditTrail auditTrail = mock(AuditTrail.class);

    private CreateStaffService serviceActingAs(String actorRole) {
        Person base = new Person();
        base.setId(NEW_ID);
        base.setStatus(StatusType.ACTIVE);
        base.setAddress(new Address());
        when(personFactory.createBasePerson(any(), any(), any())).thenReturn(base);
        for (String roleName : new String[]{"DOCENTE", "ADMINISTRATIVO"}) {
            Role role = new Role();
            role.setName(roleName);
            when(roleRepository.findByName(roleName)).thenReturn(Optional.of(role));
        }
        return new CreateStaffService(new CreateStaffTransaction(personRepository, mock(StaffInstructorProfileCreator.class),
                roleRepository, personFactory, policyActingAs(actorRole), mock(PasswordResetCodeRequester.class),
                new IdentityAuditRecorder(auditTrail)));
    }

    private static CreateStaffRequest request(String role) {
        return new CreateStaffRequest("Ana", "Perez", "ana@example.com", "30111222", null, null, null, null, null,
                null, null, null, role);
    }

    @Test
    void creatingTeacherRecordsAuditWithInitialRoleAndNoSensitiveData() {
        serviceActingAs("ADMINISTRATIVO").create(request("DOCENTE"));

        verify(auditTrail).record(new AuditRecord(AuditAction.STAFF_CREATED, AuditEntityType.PERSON, NEW_ID,
                null, "DOCENTE", null));
    }

    @Test
    void creatingAdministrativeRecordsThatRoleAsInitialRole() {
        serviceActingAs("DIRECTOR").create(request("ADMINISTRATIVO"));

        verify(auditTrail).record(new AuditRecord(AuditAction.STAFF_CREATED, AuditEntityType.PERSON, NEW_ID,
                null, "ADMINISTRATIVO", null));
    }

    @Test
    void invalidRoleIsNotAudited() {
        CreateStaffService service = serviceActingAs("ADMINISTRATIVO");

        assertThatThrownBy(() -> service.create(request("ALUMNO"))).isInstanceOf(RuntimeException.class);

        verifyNoInteractions(auditTrail);
    }

    @Test
    void duplicateDocumentFailureIsNotAudited() {
        CreateStaffService service = serviceActingAs("ADMINISTRATIVO");
        doThrow(new DataIntegrityViolationException("dup")).when(personRepository).saveAndFlush(any());

        assertThatThrownBy(() -> service.create(request("DOCENTE"))).isInstanceOf(DniAlreadyExistsException.class);

        verifyNoInteractions(auditTrail);
    }
}
