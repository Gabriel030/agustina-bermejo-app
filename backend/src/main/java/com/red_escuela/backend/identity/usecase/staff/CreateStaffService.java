package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.identity.dto.CreateStaffRequest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.exception.DniAlreadyExistsException;
import com.red_escuela.backend.identity.exception.InvalidStaffRoleException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.specialist.PersonFactory;
import com.red_escuela.backend.identity.specialist.StaffInstructorProfileCreator;
import com.red_escuela.backend.identity.specialist.StaffRoleAssignmentPolicy;
import com.red_escuela.backend.identity.specialist.PasswordResetCodeRequester;
import com.red_escuela.backend.identity.exception.RoleNotFoundException;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class CreateStaffService implements CreateStaffUseCase {
    private final PersonRepository personRepository;
    private final StaffInstructorProfileCreator staffInstructorProfileCreator;
    private final RoleRepository roleRepository;
    private final PersonFactory personFactory;
    private final StaffRoleAssignmentPolicy staffRoleAssignmentPolicy;
    private final PasswordResetCodeRequester passwordResetCodeRequester;

    public CreateStaffService(PersonRepository personRepository, StaffInstructorProfileCreator staffInstructorProfileCreator,
                               RoleRepository roleRepository, PersonFactory personFactory,
                               StaffRoleAssignmentPolicy staffRoleAssignmentPolicy,
                               PasswordResetCodeRequester passwordResetCodeRequester) {
        this.personRepository = personRepository;
        this.staffInstructorProfileCreator = staffInstructorProfileCreator;
        this.roleRepository = roleRepository;
        this.personFactory = personFactory;
        this.staffRoleAssignmentPolicy = staffRoleAssignmentPolicy;
        this.passwordResetCodeRequester = passwordResetCodeRequester;
    }

    @Override
    @Transactional
    public StaffUseCaseResult create(CreateStaffRequest request) {
        String requestedRole = request.role();
        if (!StaffRoleAssignmentPolicy.STAFF_ROLES.contains(requestedRole)) {
            throw new InvalidStaffRoleException(requestedRole);
        }
        staffRoleAssignmentPolicy.requireCanAssign(requestedRole);

        Person person = personFactory.createBasePerson(request.nombre(), request.apellido(), request.email());
        person.changeDocument(request.documentType(), request.dni());
        person.setUsername(person.getDni());

        var address = person.getAddress();
        address.setStreet(request.street());
        address.setNumber(request.number());
        address.setFloor(request.floor());
        address.setCity(request.city());
        address.setState(request.state());
        address.setPostalCode(request.postalCode());
        address.setCountry(request.country());

        Role role = roleRepository.findByName(requestedRole)
                .orElseThrow(() -> new RoleNotFoundException(requestedRole));
        person.getRoles().add(role);

        try {
            personRepository.saveAndFlush(person);
        } catch (DataIntegrityViolationException e) {
            throw new DniAlreadyExistsException(person.getDocumentType(), person.getDni());
        }

        if ("DOCENTE".equals(requestedRole)) {
            staffInstructorProfileCreator.createInstructorProfileFor(person);
        }

        passwordResetCodeRequester.requestPasswordResetCode(person.emailValue());

        return new StaffUseCaseResult(person, requestedRole);
    }
}
