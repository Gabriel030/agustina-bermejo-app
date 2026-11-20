alter table institution
    drop column if exists facebook,
    drop column if exists tweeter,
    drop column if exists pref_notas,
    drop column if exists pref_calendario,
    drop column if exists pref_asignar_curso,
    drop column if exists sms_url,
    drop column if exists sms_usuario,
    drop column if exists sms_password;
