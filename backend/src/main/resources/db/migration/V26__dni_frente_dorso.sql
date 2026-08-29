-- Épica 7 Fase 3 (UX del wizard de inscripción): DocumentType pasa de
-- DNI/ANALITICO_SECUNDARIO/FOTO a DNI_FRENTE/DNI_DORSO/ANALITICO_SECUNDARIO —
-- se pide frente y dorso por separado, FOTO se descarta (no se pedía en
-- ningún flujo real, solo estaba en el modelo original).
--
-- Datos existentes (solo dev/QA, sin datos de producción reales todavía):
-- toda fila DNI pasa a DNI_FRENTE (aproximación razonable — no hay forma de
-- saber si el archivo subido era frente o dorso, se documenta como tal);
-- FOTO se borra directo, tipo descontinuado.
update student_document set type = 'DNI_FRENTE' where type = 'DNI';
delete from student_document where type = 'FOTO';
