package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.identity.dto.CreateStaffRequest;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.exception.DniAlreadyExistsException;
import com.red_escuela.backend.identity.exception.InvalidStaffRoleException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.service.PersonFactory;
import com.red_escuela.backend.identity.service.StaffRoleAssignmentPolicy;
import com.red_escuela.backend.identity.usecase.auth.RequestPasswordResetUseCase;
import com.red_escuela.backend.exception.RoleNotFoundException;
import com.red_escuela.backend.academicstructure.repository.InstructorRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class CreateStaffService implements CreateStaffUseCase {
    private final PersonRepository personRepository;
    private final InstructorRepository instructorRepository;
    private final RoleRepository roleRepository;
    private final PersonFactory personFactory;
    private final StaffRoleAssignmentPolicy staffRoleAssignmentPolicy;
    private final RequestPasswordResetUseCase requestPasswordResetUseCase;

    public CreateStaffService(PersonRepository personRepository, InstructorRepository instructorRepository,
                               RoleRepository roleRepository, PersonFactory personFactory,
                               StaffRoleAssignmentPolicy staffRoleAssignmentPolicy,
                               RequestPasswordResetUseCase requestPasswordResetUseCase) {
        this.personRepository = personRepository;
        this.instructorRepository = instructorRepository;
        this.roleRepository = roleRepository;
        this.personFactory = personFactory;
        this.staffRoleAssignmentPolicy = staffRoleAssignmentPolicy;
        this.requestPasswordResetUseCase = requestPasswordResetUseCase;
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
        person.setDni(request.dni());
        person.setUsername(request.dni());

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
            throw new DniAlreadyExistsException(request.dni());
        }

        if ("DOCENTE".equals(requestedRole)) {
            Instructor instructor = new Instructor();
            instructor.setPerson(person);
            instructorRepository.save(instructor);
        }

        requestPasswordResetUseCase.requestReset(person.emailValue());

        return new StaffUseCaseResult(person, requestedRole);
    }
}
