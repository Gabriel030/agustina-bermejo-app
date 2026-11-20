package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.shared.domain.Address;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Address a where a.id in :ids")
    int deleteByIdIn(@Param("ids") Collection<Long> ids);
}
