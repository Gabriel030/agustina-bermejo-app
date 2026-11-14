package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.NewsletterPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface NewsletterPreferenceRepository extends JpaRepository<NewsletterPreference, Long> {

    @Modifying
    @Query(nativeQuery = true, value = """
            insert into newsletter_preference (person_id, opted_in, updated_at)
            values (:personId, :optedIn, :updatedAt)
            on conflict (person_id) do update set opted_in = excluded.opted_in, updated_at = excluded.updated_at
            """)
    int upsert(@Param("personId") Long personId, @Param("optedIn") boolean optedIn, @Param("updatedAt") LocalDateTime updatedAt);
}
