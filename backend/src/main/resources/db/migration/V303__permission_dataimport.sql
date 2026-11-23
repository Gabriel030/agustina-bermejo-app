insert into permission (code, module, description, fixed_to_director, sensitive) values
    ('dataimport.create', 'dataimport', 'Crear lotes de importación de datos', false, false),
    ('dataimport.read', 'dataimport', 'Consultar lotes de importación, sus errores y la vista previa de reversión', false, false),
    ('dataimport.upload', 'dataimport', 'Subir archivos a un lote de importación', false, false),
    ('dataimport.validate', 'dataimport', 'Validar un lote de importación', false, false),
    ('dataimport.download', 'dataimport', 'Descargar plantillas e informes de importación', false, false),
    ('dataimport.commit', 'dataimport', 'Confirmar o reintentar la importación inicial de datos', false, true),
    ('dataimport.seal', 'dataimport', 'Sellar un lote de importación de forma irreversible', false, true),
    ('dataimport.rollback', 'dataimport', 'Revertir un lote de importación (bajas)', false, true);
