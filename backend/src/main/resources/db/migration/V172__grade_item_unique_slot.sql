delete from grade_item older
using grade_item newer
where older.id < newer.id
  and older.type = newer.type
  and coalesce(older.sequence_number, -1) = coalesce(newer.sequence_number, -1)
  and older.program_section_enrollment_id is not null
  and older.program_section_enrollment_id = newer.program_section_enrollment_id;

delete from grade_item older
using grade_item newer
where older.id < newer.id
  and older.type = newer.type
  and coalesce(older.sequence_number, -1) = coalesce(newer.sequence_number, -1)
  and older.course_section_enrollment_id is not null
  and older.course_section_enrollment_id = newer.course_section_enrollment_id;

create unique index uk_grade_item_program_slot
    on grade_item (program_section_enrollment_id, type, coalesce(sequence_number, -1))
    where program_section_enrollment_id is not null;

create unique index uk_grade_item_course_slot
    on grade_item (course_section_enrollment_id, type, coalesce(sequence_number, -1))
    where course_section_enrollment_id is not null;
