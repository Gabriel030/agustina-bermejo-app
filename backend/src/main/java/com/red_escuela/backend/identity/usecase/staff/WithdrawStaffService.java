package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.exception.PersonNotFoundException;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.specialist.StaffRoleAssignmentPolicy;
import com.red_escuela.backend.identity.specialist.SessionRevoker;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class WithdrawStaffService implements WithdrawStaffUseCase {

    private static final Logger log = LoggerFactory.getLogger(WithdrawStaffService.class);

    private final PersonRepository personRepository;
    private final StaffRoleAssignmentPolicy staffRoleAssignmentPolicy;
    private final SessionRevoker sessionRevoker;
    private final AuditTrail auditTrail;

    public WithdrawStaffService(PersonRepository personRepository,
                                StaffRoleAssignmentPolicy staffRoleAssignmentPolicy,
                                SessionRevoker sessionRevoker,
                                AuditTrail auditTrail) {
        this.personRepository = personRepository;
        this.staffRoleAssignmentPolicy = staffRoleAssignmentPolicy;
        this.sessionRevoker = sessionRevoker;
        this.auditTrail = auditTrail;
    }

    @Override
    @Transactional
    public void withdraw(Long personId) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        String staffRole = staffRole(person);
        staffRoleAssignmentPolicy.requireManageableOrNotFound(personId, staffRole);
        person.setStatus(StatusType.INACTIVE);
        personRepository.save(person);
        sessionRevoker.revokeAllSessions(person);
        auditTrail.record(new AuditRecord(AuditAction.STAFF_WITHDRAWN, AuditEntityType.PERSON, personId,
                null, StatusType.INACTIVE.name(), null));
        log.info("Personal dado de baja: personId={}, role={}", personId, staffRole);
    }

    private String staffRole(Person person) {
        return person.getRoles().stream()
                .map(Role::getName)
                .filter(StaffRoleAssignmentPolicy.STAFF_ROLES::contains)
                .findFirst()
                .orElseThrow(() -> new PersonNotFoundException(person.getId()));
    }
}
