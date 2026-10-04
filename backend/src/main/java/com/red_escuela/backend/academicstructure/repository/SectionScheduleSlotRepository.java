package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public interface SectionScheduleSlotRepository extends JpaRepository<SectionScheduleSlot, Long> {

    @Query("""
            select s from SectionScheduleSlot s
            left join s.subjectSection ss
            left join ss.term sst
            left join ss.academicOfferingCycle ssc
            left join s.courseSection cs
            left join cs.term cst
            left join cs.academicOfferingCycle csc
            where s.dayOfWeek = :dayOfWeek
            and s.startTime < :endTime
            and s.endTime > :startTime
            and (
                sst.id = :termId
                or cst.id = :termId
                or (sst.startDate <= :termEnd and sst.endDate >= :termStart)
                or (cst.startDate <= :termEnd and cst.endDate >= :termStart)
            )
            and (
                ss.status = com.red_escuela.backend.shared.domain.StatusType.ACTIVE
                or cs.status = com.red_escuela.backend.shared.domain.StatusType.ACTIVE
                or ssc.status in (
                    com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus.DRAFT,
                    com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus.PENDING_APPROVAL)
                or csc.status in (
                    com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus.DRAFT,
                    com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus.PENDING_APPROVAL)
            )
            """)
    List<SectionScheduleSlot> findOccupyingSlots(
            @Param("termId") Long termId,
            @Param("termStart") LocalDateTime termStart,
            @Param("termEnd") LocalDateTime termEnd,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );

    List<SectionScheduleSlot> findBySubjectSectionId(Long subjectSectionId);

    List<SectionScheduleSlot> findByCourseSectionId(Long courseSectionId);
}
