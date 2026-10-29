package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.shared.domain.EmailAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PersonRepository extends JpaRepository<Person, Long>, JpaSpecificationExecutor<Person> {

    Optional<Person> findByUsername(String username);

    @Query("select distinct p from Person p left join fetch p.roles where p.id in :personIds")
    List<Person> findAllWithRolesByIdIn(@Param("personIds") Collection<Long> personIds);

    Optional<Person> findByEmail(EmailAddress email);

    Optional<Person> findByDocumentTypeAndDni(DocumentType documentType, String dni);

    Optional<Person> findByGoogleSubjectId(String googleSubjectId);

    List<Person> findByRolesName(String roleName);
}
