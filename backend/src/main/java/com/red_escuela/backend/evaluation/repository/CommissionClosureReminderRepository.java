package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.entity.CommissionClosureReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface CommissionClosureReminderRepository extends JpaRepository<CommissionClosureReminder, Long> {

    @Modifying
    @Query(value = """
            insert into commission_closure_reminder (section_kind, section_id, days_after_term_end, sent_at)
            values (:#{#kind.name()}, :sectionId, :daysAfterTermEnd, :sentAt)
            on conflict (section_kind, section_id, days_after_term_end) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("kind") SectionKind kind, @Param("sectionId") Long sectionId,
            @Param("daysAfterTermEnd") int daysAfterTermEnd, @Param("sentAt") LocalDateTime sentAt);
}
