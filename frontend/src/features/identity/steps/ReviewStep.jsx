import { documentLabel } from '@/lib/documentType';
import { missingDocumentsSummary } from '@/features/identity/lib/studentRegistration';

function Row({ label, value }) {
  return (
    <div className="flex items-start justify-between gap-4">
      <span className="text-[12px] text-ink-faint flex-shrink-0">{label}</span>
      <span className="text-[13px] text-ink text-right">{value}</span>
    </div>
  );
}

export function ReviewStep({ values, missingDocuments }) {
  const { nombre, apellido, email, telefono, documentType, dni, street, number, floor, city, state, postalCode, country } = values;
  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-base font-semibold text-ink">Revisar y confirmar</h2>
      <p className="text-[13px] text-ink-soft">Revisá los datos: al confirmar, el alumno recibe un email para elegir su contraseña.</p>
      <div className="flex flex-col gap-3 rounded-lg border border-border p-4">
        <p className="text-[12px] font-semibold uppercase tracking-wide text-ink-faint">Datos básicos</p>
        <Row label="Nombre" value={`${nombre} ${apellido}`} />
        <Row label="Email" value={email} />
        {telefono && <Row label="Teléfono" value={telefono} />}
      </div>
      <div className="flex flex-col gap-3 rounded-lg border border-border p-4">
        <p className="text-[12px] font-semibold uppercase tracking-wide text-ink-faint">Datos personales</p>
        <Row label={documentLabel(documentType)} value={dni} />
        <Row label="Domicilio" value={[street, number, floor].filter(Boolean).join(' ')} />
        <Row label="Ciudad" value={[city, state].filter(Boolean).join(', ')} />
        {postalCode && <Row label="Código postal" value={postalCode} />}
        {country && <Row label="País" value={country} />}
      </div>
      <div className="flex flex-col gap-3 rounded-lg border border-border p-4">
        <p className="text-[12px] font-semibold uppercase tracking-wide text-ink-faint">Documentación</p>
        <p className="text-[13px] text-ink">{missingDocumentsSummary(missingDocuments)}</p>
      </div>
    </div>
  );
}
