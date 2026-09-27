package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public interface SectionScheduleSlotRepository extends JpaRepository<SectionScheduleSlot, Long> {

    @Query("""
            select s from SectionScheduleSlot s
            where s.classroom.id = :classroomId
            and s.dayOfWeek = :dayOfWeek
            and s.startTime < :endTime
            and s.endTime > :startTime
            and (:excludeSlotId is null or s.id <> :excludeSlotId)
            """)
    List<SectionScheduleSlot> findConflictingSlots(
            @Param("classroomId") Long classroomId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("excludeSlotId") Long excludeSlotId
    );

    @Query("""
            select distinct s.classroom.id from SectionScheduleSlot s
            where s.dayOfWeek = :dayOfWeek
            and s.startTime < :endTime
            and s.endTime > :startTime
            and (
                (s.subjectSection is not null and s.subjectSection.term.id = :termId)
                or
                (s.courseSection is not null and s.courseSection.term.id = :termId)
            )
            """)
    Set<Long> findOccupiedClassroomIds(
            @Param("termId") Long termId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );

    List<SectionScheduleSlot> findBySubjectSectionId(Long subjectSectionId);

    List<SectionScheduleSlot> findByCourseSectionId(Long courseSectionId);
}
