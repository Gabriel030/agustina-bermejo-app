package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.shared.domain.EmailAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PersonRepository extends JpaRepository<Person, Long>, JpaSpecificationExecutor<Person> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            update person set reset_lockout_notified_at = :now
            where id = :personId and (reset_lockout_notified_at is null or reset_lockout_notified_at <= :notifiedBefore)
            """, nativeQuery = true)
    int claimResetLockoutNotice(@Param("personId") Long personId, @Param("now") LocalDateTime now,
                                @Param("notifiedBefore") LocalDateTime notifiedBefore);

    @Modifying(flushAutomatically = true)
    @Query("update Person p set p.tokenVersion = p.tokenVersion + 1 where p.id = :personId")
    int incrementTokenVersion(@Param("personId") Long personId);

    @Query("select p.tokenVersion from Person p where p.id = :personId")
    Integer findTokenVersionById(@Param("personId") Long personId);

    Optional<Person> findByUsername(String username);

    @Query("select new com.red_escuela.backend.identity.repository.AccountAccessState(p.status, p.tokenVersion) from Person p where p.id = :personId")
    Optional<AccountAccessState> findAccessStateById(@Param("personId") Long personId);

    boolean existsByRoles_Name(String roleName);

    @Query("select distinct p from Person p left join fetch p.roles where p.id in :personIds")
    List<Person> findAllWithRolesByIdIn(@Param("personIds") Collection<Long> personIds);

    default List<Person> findAllWithRoles(
            org.springframework.data.jpa.domain.Specification<Person> spec, org.springframework.data.domain.Sort sort) {
        List<Person> people = findAll(spec, sort);
        if (!people.isEmpty()) {
            findAllWithRolesByIdIn(people.stream().map(Person::getId).toList());
        }
        return people;
    }

    Optional<Person> findByEmail(EmailAddress email);

    Optional<Person> findByDocumentTypeAndDni(DocumentType documentType, String dni);

    Optional<Person> findByGoogleSubjectId(String googleSubjectId);

    List<Person> findByRolesName(String roleName);
}
