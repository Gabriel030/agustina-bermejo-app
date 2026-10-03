update subject set max_final_exam_attempts = 1 where max_final_exam_attempts is null;
alter table subject alter column max_final_exam_attempts set default 1;
alter table subject alter column max_final_exam_attempts set not null;

update course_subject set max_final_exam_attempts = 1 where max_final_exam_attempts is null;
alter table course_subject alter column max_final_exam_attempts set default 1;
alter table course_subject alter column max_final_exam_attempts set not null;
