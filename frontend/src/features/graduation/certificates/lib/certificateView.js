const CONDITION_BY_KIND = {
  GRADUATION: 'el título',
  REGULAR: 'la condición de alumno regular',
};

export function formatDay(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value ?? '');
  return match ? `${match[3]}/${match[2]}/${match[1]}` : '';
}

export function formatDayAndTime(date) {
  const pad = (number) => String(number).padStart(2, '0');
  return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function verdictOf(certificate) {
  const condition = CONDITION_BY_KIND[certificate.documentKind] ?? 'el documento';
  if (certificate.status === 'REVOKED') {
    return { icon: 'ban', tone: 'danger', title: 'Revocado', message: `El instituto lo anuló. Ya no acredita ${condition}.` };
  }
  if (certificate.status === 'EXPIRED') {
    const since = certificate.expiresAt ? `Venció el ${formatDay(certificate.expiresAt)}. ` : '';
    return { icon: 'clock', tone: 'warning', title: 'Vencido', message: `${since}Ya no acredita ${condition}.` };
  }
  const message = certificate.documentKind === 'REGULAR' ? 'Documento auténtico y vigente.' : 'Documento auténtico.';
  return { icon: 'ok', tone: 'success', title: 'Válido', message };
}

export function documentTitle(certificate) {
  return certificate.documentKind === 'GRADUATION' ? 'Certificado de egreso' : 'Constancia de alumno regular';
}

export function detailRows(certificate) {
  const rows = [{ label: 'Documento', value: certificate.maskedDocument }];
  if (certificate.documentKind === 'GRADUATION') {
    rows.push({ label: 'Título', value: certificate.titleName });
    rows.push({ label: 'Fecha de egreso', value: formatDay(certificate.graduationDate) });
    const hours = certificate.hours ? ` · ${certificate.hours.toLocaleString('es-AR')} horas` : '';
    rows.push({ label: 'Registro', value: `N.º ${certificate.registryNumber}${hours}` });
  } else {
    rows.push({ label: 'Carrera', value: certificate.titleName });
    rows.push({ label: 'Emitida', value: formatDay(certificate.issuedAt) });
    if (certificate.status !== 'REVOKED') {
      rows.push({
        label: certificate.status === 'EXPIRED' ? 'Venció' : 'Vigente hasta',
        value: formatDay(certificate.expiresAt),
      });
    }
  }
  if (certificate.status === 'REVOKED') {
    rows.push({ label: 'Revocado el', value: formatDay(certificate.revokedAt) });
  }
  return rows;
}
