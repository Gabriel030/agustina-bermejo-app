alter table subject_section alter column max_absence_percentage type numeric(7,6)
    using (max_absence_percentage / 100)::numeric(7,6);
alter table course_section alter column max_absence_percentage type numeric(7,6)
    using (max_absence_percentage / 100)::numeric(7,6);

alter table subject_section add constraint ck_subject_section_max_absence_percentage
    check (max_absence_percentage between 0 and 1);
alter table course_section add constraint ck_course_section_max_absence_percentage
    check (max_absence_percentage between 0 and 1);
