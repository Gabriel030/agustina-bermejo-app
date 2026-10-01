import { Input } from '@/components/ui/input';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { fechaEnPalabras, formatearFechaCorta, formatearFechaHora } from '@/lib/avisos';

const OPCIONES_CADUCIDAD = [
  { valor: 'siempre', label: 'Siempre' },
  { valor: '1s', label: '1 semana' },
  { valor: '1m', label: '1 mes' },
  { valor: 'otra', label: 'Otra fecha…' },
];

function Chip({ activo, onClick, children }) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={activo}
      onClick={onClick}
      className={`rounded-xl border px-3.5 py-1.5 text-[12.5px] font-bold transition-all focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand ${
        activo
          ? 'border-transparent bg-gradient-to-br from-brand to-accent-2 text-white'
          : 'border-border text-ink-soft hover:border-brand hover:text-ink'
      }`}
    >
      {children}
    </button>
  );
}

export function EnvioYCaducidad({ abierto, onAbrir, valores, onChange, yaEnviadoEl }) {
  const { cuando, scheduledFor, caducidad, expiresAt } = valores;

  if (!abierto) {
    return (
      <div className="flex flex-wrap items-center gap-2.5 rounded-2xl border border-dashed border-border px-4 py-3 text-[12.5px] text-ink-soft">
        <span>
          {yaEnviadoEl ? 'Se envió el ' : 'Se envía '}
          <span className="font-bold text-ink">
            {yaEnviadoEl
              ? formatearFechaHora(yaEnviadoEl)
              : cuando === 'programar' && scheduledFor
                ? fechaEnPalabras(scheduledFor)
                : 'ahora'}
          </span>
        </span>
        <span className="text-ink-faint">·</span>
        <span>
          Se muestra{' '}
          <span className="font-bold text-ink">
            {caducidad === 'siempre' ? 'siempre' : `hasta el ${formatearFechaCorta(expiresAt) ?? '—'}`}
          </span>
        </span>
        <Button type="button" variant="link" className="ml-auto h-auto p-0" onClick={onAbrir}>
          Cambiar fechas
        </Button>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-1.5" hidden={!!yaEnviadoEl}>
        <span className="text-[13px] font-semibold text-ink" id="et-cuando">
          ¿Cuándo se envía?
        </span>
        <div className="flex flex-wrap gap-2" role="radiogroup" aria-labelledby="et-cuando">
          <Chip activo={cuando === 'ahora'} onClick={() => onChange({ ...valores, cuando: 'ahora', scheduledFor: null })}>
            Ahora
          </Chip>
          <Chip activo={cuando === 'programar'} onClick={() => onChange({ ...valores, cuando: 'programar' })}>
            Programar…
          </Chip>
        </div>
        {cuando === 'programar' && (
          <div className="mt-2 flex flex-col gap-1.5">
            <div className="flex flex-wrap items-center gap-2.5">
              <Input
                type="datetime-local"
                className="max-w-[240px]"
                value={scheduledFor ?? ''}
                onChange={(e) => onChange({ ...valores, scheduledFor: e.target.value })}
                aria-label="Fecha y hora de envío"
              />
              <StatusPill tone="neutral">UTC−3</StatusPill>
            </div>
            {scheduledFor && <span className="text-[11.5px] text-ink-faint">Sale el {fechaEnPalabras(scheduledFor)}.</span>}
          </div>
        )}
      </div>

      <div className="flex flex-col gap-1.5">
        <span className="text-[13px] font-semibold text-ink" id="et-caducidad">
          ¿Hasta cuándo se muestra?
        </span>
        <div className="flex flex-wrap gap-2" role="radiogroup" aria-labelledby="et-caducidad">
          {OPCIONES_CADUCIDAD.map((opcion) => (
            <Chip
              key={opcion.valor}
              activo={caducidad === opcion.valor}
              onClick={() => onChange({ ...valores, caducidad: opcion.valor })}
            >
              {opcion.label}
            </Chip>
          ))}
        </div>
        {caducidad === 'otra' && (
          <Input
            type="date"
            className="mt-2 max-w-[200px]"
            value={expiresAt ?? ''}
            onChange={(e) => onChange({ ...valores, expiresAt: e.target.value })}
            aria-label="Fecha de vencimiento"
          />
        )}
      </div>
    </div>
  );
}
