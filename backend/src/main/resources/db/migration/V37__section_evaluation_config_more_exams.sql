-- Una materia anual puede tener más parciales que una cuatrimestral (ver
-- EvaluationScheme.examCountExpected, que ya distingue esto a nivel institucional).
-- exam1_date/exam2_date alcanzaban solo para el caso cuatrimestral (2 parciales).
alter table section_evaluation_config
    add column exam3_date date,
    add column exam4_date date;
