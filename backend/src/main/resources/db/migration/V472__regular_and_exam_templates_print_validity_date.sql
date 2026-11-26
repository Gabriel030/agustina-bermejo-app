insert into certificate_template_version (template_id, version_number, status, based_on_version_number, institute_name, logo_image_id, title,
                                          intro_text, approval_text, dictation_text, qr_label, footer_legend, signatory1_id, signatory2_id,
                                          updated_at, published_at, published_by_name)
select v.template_id, v.version_number + 1, 'PUBLISHED', v.version_number, v.institute_name, v.logo_image_id, v.title,
       v.intro_text, 'Esta constancia es válida hasta el {validUntil}.', v.dictation_text, v.qr_label, v.footer_legend,
       v.signatory1_id, v.signatory2_id, now(), now(), 'Sistema'
from certificate_template_version v
join certificate_template t on t.id = v.template_id
where t.document_type in ('REGULAR_STUDENT_CERTIFICATE', 'EXAM_CERTIFICATE')
  and v.status = 'PUBLISHED'
  and v.version_number = (select max(w.version_number) from certificate_template_version w where w.template_id = v.template_id and w.status = 'PUBLISHED')
  and v.approval_text = 'Esta constancia tiene validez de 30 días desde su fecha de emisión.';
