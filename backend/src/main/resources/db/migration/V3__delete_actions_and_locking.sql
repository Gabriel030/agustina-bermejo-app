-- Etapa 2: acciones de borrado explícitas + optimistic locking (hallazgos d y g del análisis).

-- Optimistic locking en entidades con escritura concurrente típica (cupos, notas).
alter table course_section add column version bigint not null default 0;
alter table program_section add column version bigint not null default 0;
alter table grade_item add column version bigint not null default 0;
alter table course_enrollment add column version bigint not null default 0;
alter table program_enrollment add column version bigint not null default 0;
alter table course_section_enrollment add column version bigint not null default 0;
alter table program_section_enrollment add column version bigint not null default 0;

-- ON DELETE CASCADE explícito, solo en las relaciones donde el código ya declaraba
-- esa intención vía cascade = CascadeType.ALL de Hibernate (antes solo funcionaba
-- si el borrado pasaba por el EntityManager). El resto de las FKs se queda RESTRICT
-- (comportamiento por default de H2, sin cambios).
alter table course_subject drop constraint FK3aybh24ba5rqjcu3c9cuo40e4;
alter table course_subject add constraint FK3aybh24ba5rqjcu3c9cuo40e4 foreign key (course_id) references course on delete cascade;

alter table course_subject_prerequisite drop constraint FKt42w46ubqb8kskdu6amtjx2ne;
alter table course_subject_prerequisite add constraint FKt42w46ubqb8kskdu6amtjx2ne foreign key (course_subject_id) references course_subject on delete cascade;

alter table course_section_instructor drop constraint FK31l43x39ogmrx7b7q1km5hndr;
alter table course_section_instructor add constraint FK31l43x39ogmrx7b7q1km5hndr foreign key (course_section_id) references course_section on delete cascade;

alter table course_section_enrollment drop constraint FKoj50bppic5e0f1mki544dg0pa;
alter table course_section_enrollment add constraint FKoj50bppic5e0f1mki544dg0pa foreign key (course_enrollment_id) references course_enrollment on delete cascade;

alter table course_enrollment drop constraint FKc5aniwy1axq8343gtpm50sdlj;
alter table course_enrollment add constraint FKc5aniwy1axq8343gtpm50sdlj foreign key (student_id) references student on delete cascade;

alter table program_enrollment drop constraint FKlemcgxfld9p36kqj8xpe65sfd;
alter table program_enrollment add constraint FKlemcgxfld9p36kqj8xpe65sfd foreign key (student_id) references student on delete cascade;

alter table program_subject drop constraint FK6u3q288n6sweoanwg12pyp2qm;
alter table program_subject add constraint FK6u3q288n6sweoanwg12pyp2qm foreign key (program_id) references program on delete cascade;

alter table program_subject_prerequisite drop constraint FKmssm5tldo5g3w4ckgh690gamk;
alter table program_subject_prerequisite add constraint FKmssm5tldo5g3w4ckgh690gamk foreign key (program_subject_id) references program_subject on delete cascade;

alter table program_section_instructor drop constraint FK8dxigio7hr5ew3ta6di5fh7bv;
alter table program_section_instructor add constraint FK8dxigio7hr5ew3ta6di5fh7bv foreign key (program_section_id) references program_section on delete cascade;

alter table program_section_enrollment drop constraint FK97wtmkm0tptr06f0yx0w2krdx;
alter table program_section_enrollment add constraint FK97wtmkm0tptr06f0yx0w2krdx foreign key (program_enrollment_id) references program_enrollment on delete cascade;
