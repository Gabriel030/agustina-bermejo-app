alter table certificate_template add column if not exists document_type varchar(40);
alter table certificate_template alter column kind drop not null;
alter table certificate_template drop constraint if exists certificate_template_document_type_enum;
alter table certificate_template drop constraint if exists ck_certificate_template_single_discriminator;
alter table certificate_template
    add constraint certificate_template_document_type_enum check (document_type in
        ('REGULAR_STUDENT_CERTIFICATE', 'APPROVED_SUBJECTS_CERTIFICATE', 'EXAM_REGISTRATION_CERTIFICATE', 'EXAM_CERTIFICATE')),
    add constraint ck_certificate_template_single_discriminator check ((kind is null) <> (document_type is null));
create unique index if not exists uq_certificate_template_document_type on certificate_template (document_type) where document_type is not null;

insert into certificate_template (document_type)
select d from (values ('REGULAR_STUDENT_CERTIFICATE'), ('APPROVED_SUBJECTS_CERTIFICATE'), ('EXAM_REGISTRATION_CERTIFICATE'), ('EXAM_CERTIFICATE')) as v(d)
where not exists (select 1 from certificate_template t where t.document_type = v.d);

insert into certificate_template_version (template_id, version_number, status, institute_name, title, intro_text, approval_text,
                                          dictation_text, qr_label, footer_legend, updated_at, published_at, published_by_name)
select t.id, 1, 'PUBLISHED', 'INSTITUTO AGUSTINA BERMEJO',
       case t.document_type
           when 'REGULAR_STUDENT_CERTIFICATE' then 'CONSTANCIA DE ALUMNO REGULAR'
           when 'APPROVED_SUBJECTS_CERTIFICATE' then 'CONSTANCIA DE MATERIAS APROBADAS'
           when 'EXAM_REGISTRATION_CERTIFICATE' then 'CONSTANCIA DE INSCRIPCIÓN A EXAMEN'
           else 'CONSTANCIA DE EXAMEN RENDIDO' end,
       case t.document_type
           when 'REGULAR_STUDENT_CERTIFICATE' then 'Se deja constancia de que {holder}, {documentType} {documentNumber}, es alumno/a regular de la carrera {titleName}.'
           when 'APPROVED_SUBJECTS_CERTIFICATE' then 'Se deja constancia de que {holder}, {documentType} {documentNumber}, cursa la carrera {titleName}.'
           when 'EXAM_REGISTRATION_CERTIFICATE' then 'Se deja constancia de que {holder}, {documentType} {documentNumber}, se encuentra inscripto/a para rendir {subjectName} el {examDate}.'
           else 'Se deja constancia de que {holder}, {documentType} {documentNumber}, se presentó a rendir examen final de {subjectName} el {examDate}.' end,
       case t.document_type
           when 'APPROVED_SUBJECTS_CERTIFICATE' then 'Promedio general: {average}. Tiene aprobado el {completionPercent} del total del programa.'
           when 'EXAM_REGISTRATION_CERTIFICATE' then 'Vigente hasta el día del examen. Si la mesa cambia, verificá con el código QR.'
           else 'Esta constancia tiene validez de 30 días desde su fecha de emisión.' end,
       case t.document_type
           when 'APPROVED_SUBJECTS_CERTIFICATE' then null
           else 'Se extiende a pedido del interesado para ser presentado ante: {recipient}' end,
       'Verificá la autenticidad',
       case t.document_type
           when 'APPROVED_SUBJECTS_CERTIFICATE' then 'Constancia emitida digitalmente. Su validez puede comprobarse en la web del instituto con el código de verificación. No es un título ni un certificado analítico.'
           when 'EXAM_CERTIFICATE' then 'Constancia emitida digitalmente. Su validez puede comprobarse en la web del instituto con el código de verificación. Acredita únicamente la presentación al examen, no su resultado.'
           else 'Constancia emitida digitalmente. Su validez puede comprobarse en la web del instituto con el código de verificación.' end,
       now(), now(), 'Sistema'
from certificate_template t
where t.document_type is not null
  and not exists (select 1 from certificate_template_version v where v.template_id = t.id);
