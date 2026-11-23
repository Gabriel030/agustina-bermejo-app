alter table certificate_template add column document_type varchar(40);
alter table certificate_template alter column kind drop not null;
alter table certificate_template
    add constraint certificate_template_document_type_enum check (document_type in
        ('REGULAR_STUDENT_CERTIFICATE', 'APPROVED_SUBJECTS_CERTIFICATE', 'EXAM_REGISTRATION_CERTIFICATE', 'EXAM_CERTIFICATE')),
    add constraint ck_certificate_template_single_discriminator check ((kind is null) <> (document_type is null));
create unique index uq_certificate_template_document_type on certificate_template (document_type) where document_type is not null;

insert into certificate_template (document_type) values
    ('REGULAR_STUDENT_CERTIFICATE'), ('APPROVED_SUBJECTS_CERTIFICATE'), ('EXAM_REGISTRATION_CERTIFICATE'), ('EXAM_CERTIFICATE');

insert into certificate_template_version (template_id, version_number, status, institute_name, title, intro_text, approval_text,
                                          dictation_text, qr_label, footer_legend, first_signer_name, first_signer_role,
                                          second_signer_name, second_signer_role, updated_at, published_at, published_by_name)
select t.id, 1, 'PUBLISHED', 'INSTITUTO AGUSTINA BERMEJO',
       case t.document_type
           when 'REGULAR_STUDENT_CERTIFICATE' then 'CONSTANCIA DE ALUMNO REGULAR'
           when 'APPROVED_SUBJECTS_CERTIFICATE' then 'CONSTANCIA DE MATERIAS APROBADAS'
           when 'EXAM_REGISTRATION_CERTIFICATE' then 'CONSTANCIA DE INSCRIPCIÓN A EXAMEN'
           else 'CONSTANCIA DE EXAMEN RENDIDO' end,
       case t.document_type
           when 'REGULAR_STUDENT_CERTIFICATE' then 'Se certifica que {holder}, {documentType} {documentNumber}, es alumno regular de {titleName}.'
           when 'APPROVED_SUBJECTS_CERTIFICATE' then 'Se certifica que {holder}, {documentType} {documentNumber}, cursa {titleName} y ha aprobado las siguientes materias.'
           when 'EXAM_REGISTRATION_CERTIFICATE' then 'Se certifica que {holder}, {documentType} {documentNumber}, está inscripto a rendir {subjectName} el {examDate}.'
           else 'Se certifica que {holder}, {documentType} {documentNumber}, rindió examen de {subjectName} el {examDate}.' end,
       case t.document_type
           when 'APPROVED_SUBJECTS_CERTIFICATE' then 'Promedio: {average}. Avance del programa: {completionPercent}.'
           else 'Se extiende a pedido del interesado para ser presentado ante: {recipient}.' end,
       null, 'QR DE VALIDACIÓN DE AUTENTICIDAD',
       'Documento emitido por el Instituto Agustina Bermejo. Verificá su autenticidad escaneando el código QR.',
       null, null, null, null, now(), now(), 'Sistema'
from certificate_template t
where t.document_type is not null;
