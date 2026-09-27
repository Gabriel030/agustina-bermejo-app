import { CheckCircle2, Clock, Mail } from 'lucide-react';

export function ResumenStep({ alumno, esAnonimo }) {
  const { nombre, apellido, email, telefono, dni, street, number, floor, city, state, postalCode, country } = alumno;

  return (
    <div className="flex flex-col gap-6">
      <h2 className="text-base font-semibold text-ink">Resumen</h2>

      <div className="rounded-lg border border-border p-4 flex flex-col gap-3">
        <p className="text-[12px] font-semibold uppercase tracking-wide text-muted-foreground">Datos personales</p>
        <Row label="Nombre" value={`${nombre} ${apellido}`} />
        <Row label="Email" value={email} />
        {telefono && <Row label="Teléfono" value={telefono} />}
      </div>

      {(dni || street || city) && (
        <div className="rounded-lg border border-border p-4 flex flex-col gap-3">
          <p className="text-[12px] font-semibold uppercase tracking-wide text-muted-foreground">Datos de confirmación</p>
          {dni && <Row label="DNI" value={dni} />}
          {street && <Row label="Domicilio" value={[street, number, floor].filter(Boolean).join(' ')} />}
          {city && <Row label="Ciudad" value={[city, state].filter(Boolean).join(', ')} />}
          {postalCode && <Row label="Código postal" value={postalCode} />}
          {country && <Row label="País" value={country} />}
        </div>
      )}

      {esAnonimo ? (
        <div className="flex gap-3 rounded-lg border border-accent-soft bg-accent-soft p-4">
          <Mail size={18} className="mt-0.5 flex-shrink-0 text-brand" />
          <div>
            <p className="text-[13px] font-medium text-primary">Verificación de email pendiente</p>
            <p className="mt-0.5 text-[12px] text-ink-soft">
              Se envió un correo a <strong>{email}</strong> para confirmar tu inscripción.
            </p>
          </div>
        </div>
      ) : (
        <div className="flex gap-3 rounded-lg border border-warning-bg bg-warning-bg p-4">
          <Clock size={18} className="mt-0.5 flex-shrink-0 text-warning" />
          <div>
            <p className="text-[13px] font-medium text-warning">Alta en proceso</p>
            <p className="mt-0.5 text-[12px] text-warning">
              El alumno queda como prospecto hasta que el email sea verificado. Podés reenviar la verificación desde el padrón.
            </p>
          </div>
        </div>
      )}

      <div className="flex items-center gap-2 text-success">
        <CheckCircle2 size={18} />
        <p className="text-[13px] font-medium">Alta registrada correctamente</p>
      </div>
    </div>
  );
}

function Row({ label, value }) {
  return (
    <div className="flex items-start justify-between gap-4">
      <span className="text-[12px] text-muted-foreground flex-shrink-0">{label}</span>
      <span className="text-[13px] text-ink text-right">{value}</span>
    </div>
  );
}
