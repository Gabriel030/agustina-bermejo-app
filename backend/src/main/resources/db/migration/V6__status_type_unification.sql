-- Hallazgo p: Boolean status vs StatusType. Person ya usaba StatusType (V2); el resto de las
-- entidades con status usaban un booleano binario que no puede representar "borrado lógico"
-- (DELETED) separado de "inactivo temporalmente" (INACTIVE) — dos estados de negocio distintos
-- colapsados en un solo bit. Se unifica a StatusType en las 9 tablas restantes.

alter table course add column status_str varchar(20);
update course set status_str = case status when true then 'ACTIVE' when false then 'INACTIVE' end;
alter table course drop column status;
alter table course alter column status_str rename to status;

alter table course_subject add column status_str varchar(20);
update course_subject set status_str = case status when true then 'ACTIVE' when false then 'INACTIVE' end;
alter table course_subject drop column status;
alter table course_subject alter column status_str rename to status;

alter table course_section add column status_str varchar(20);
update course_section set status_str = case status when true then 'ACTIVE' when false then 'INACTIVE' end;
alter table course_section drop column status;
alter table course_section alter column status_str rename to status;

alter table program add column status_str varchar(20);
update program set status_str = case status when true then 'ACTIVE' when false then 'INACTIVE' end;
alter table program drop column status;
alter table program alter column status_str rename to status;

alter table program_subject add column status_str varchar(20);
update program_subject set status_str = case status when true then 'ACTIVE' when false then 'INACTIVE' end;
alter table program_subject drop column status;
alter table program_subject alter column status_str rename to status;

alter table program_section add column status_str varchar(20);
update program_section set status_str = case status when true then 'ACTIVE' when false then 'INACTIVE' end;
alter table program_section drop column status;
alter table program_section alter column status_str rename to status;

alter table study_program add column status_str varchar(20);
update study_program set status_str = case status when true then 'ACTIVE' when false then 'INACTIVE' end;
alter table study_program drop column status;
alter table study_program alter column status_str rename to status;

alter table study_program_course add column status_str varchar(20);
update study_program_course set status_str = case status when true then 'ACTIVE' when false then 'INACTIVE' end;
alter table study_program_course drop column status;
alter table study_program_course alter column status_str rename to status;

alter table term add column status_str varchar(20);
update term set status_str = case status when true then 'ACTIVE' when false then 'INACTIVE' end;
alter table term drop column status;
alter table term alter column status_str rename to status;
