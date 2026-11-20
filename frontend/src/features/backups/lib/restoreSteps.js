const SERVER_ACTOR = 'Lo hace la persona técnica en el servidor';

export const RECOVERY_PLAIN_LINES = [
  ['Qué pasa', 'Se recupera un dato borrado o cambiado por error desde una copia, sin tocar lo que está en uso.'],
  ['Cuánto tarda', 'Depende de lo que haya que buscar. La app sigue andando.'],
  ['Quién lo hace', 'La persona técnica en el servidor.'],
  ['A quién avisar', 'A soporte técnico, con la fecha y qué se perdió.'],
];

export const REPLACEMENT_PLAIN_LINES = [
  ['Qué pasa', 'Se vuelve a una copia completa cuando la base en uso se dañó. Se pierde lo cargado después de esa copia.'],
  ['Cuánto tarda', 'Unos minutos con la app parada.'],
  ['Quién lo hace', 'La persona técnica en el servidor.'],
  ['A quién avisar', 'Primero a soporte técnico. Después, a todos los usuarios antes de parar la app y al reabrirla.'],
];

export const PRE_OPEN_CHECKS = [
  'La app arranca.',
  'La versión de la base coincide con la del código.',
  'Las cantidades coinciden con las del backup.',
  'Se entra con un usuario de prueba.',
  'Se abre un certificado y un archivo subido.',
];

const RESTORE_SCRIPT = 'docker compose exec backup restore.sh';
const AGE_KEY_RUN = 'docker compose run --rm -v $PWD/clave.txt:/clave.txt:ro -e AGE_IDENTITY_FILE=/clave.txt backup restore.sh';

export function recoverySteps() {
  return [
    {
      title: 'Elegí el backup',
      description: 'Tomá el de una fecha anterior a la pérdida. El script verifica que esté sano antes de tocar nada.',
      actor: SERVER_ACTOR,
      command: 'docker compose exec backup ls -la /backups',
    },
    {
      title: 'Restauralo en una base aparte',
      description: 'La base en uso no se toca. Si el backup está cifrado, hace falta la clave privada.',
      actor: SERVER_ACTOR,
      command: `${RESTORE_SCRIPT} /backups/<archivo>.dump --db agustina_bermejo_recuperada\n# cifrado:\n${AGE_KEY_RUN} /backups/<archivo>.dump.age --db agustina_bermejo_recuperada`,
    },
    {
      title: 'Buscá y copiá solo lo necesario',
      description: 'Consultá la base aparte y pasá a la base en uso solo los datos que faltan.',
      actor: SERVER_ACTOR,
      command: 'docker compose exec postgres psql -U "$POSTGRES_USER" -d agustina_bermejo_recuperada',
    },
    {
      title: 'Borrá la base aparte',
      actor: SERVER_ACTOR,
      command: 'docker compose exec -T postgres dropdb -U "$POSTGRES_USER" agustina_bermejo_recuperada',
    },
  ];
}

export function replacementSteps() {
  return [
    { title: 'Avisá a los usuarios', description: 'Que guarden lo que están haciendo: la app va a estar parada unos minutos.' },
    { title: 'Pará la app', actor: SERVER_ACTOR, command: 'docker compose stop backend' },
    {
      title: 'Restaurá el backup en una base nueva',
      description: 'El script verifica el checksum y descifra si hace falta la clave privada.',
      actor: SERVER_ACTOR,
      command: `${RESTORE_SCRIPT} /backups/<archivo>.dump --db agustina_bermejo_nueva\n# cifrado:\n${AGE_KEY_RUN} /backups/<archivo>.dump.age --db agustina_bermejo_nueva`,
    },
    {
      title: 'Cambiá de base',
      description:
        'Renombrá la dañada y poné la restaurada con el nombre original. No hace falta cambiar la configuración de la app (DB_URL, POSTGRES_DB). Para volver atrás, renombrá al revés.',
      actor: SERVER_ACTOR,
      command:
        'docker compose exec -T postgres psql -U "$POSTGRES_USER" -d postgres \\\n' +
        '  -c "ALTER DATABASE agustina_bermejo RENAME TO agustina_bermejo_danada_<fecha>;" \\\n' +
        '  -c "ALTER DATABASE agustina_bermejo_nueva RENAME TO agustina_bermejo;"',
    },
    {
      title: 'Restaurá los archivos subidos y los certificados',
      description: 'Del mismo momento que la base.',
      actor: SERVER_ACTOR,
      command: 'age -d -i <clave privada> <archivo de archivos>.age \\\n  | zstd -d | docker run --rm -i -v app_data:/data alpine tar -x -C /data',
    },
    { title: 'Levantá la app', actor: SERVER_ACTOR, command: 'docker compose start backend' },
    {
      title: 'Verificá antes de abrir',
      description: 'Todos tienen que dar bien:',
      checks: PRE_OPEN_CHECKS,
      actor: SERVER_ACTOR,
      command:
        'curl -fsS https://<dominio>/api/actuator/health\n' +
        'docker compose exec -T postgres psql -U "$POSTGRES_USER" -d agustina_bermejo \\\n' +
        '  -tc "select max(version) from flyway_schema_history where success"',
    },
    { title: 'Reabrí y avisá', description: 'Contales hasta qué fecha y hora quedaron los datos.' },
  ];
}
