import { Input } from '@/components/ui/input';
import { nombreDeInstancia } from '@/lib/avisos';

function Chip({ activo, onClick, rol, children }) {
  const estado = rol === 'radio' ? { 'aria-checked': activo } : { 'aria-pressed': activo };
  return (
    <button
      type="button"
      role={rol}
      {...estado}
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

export function ParametrosDeSegmento({ segmento, spec, onChange }) {
  if (!segmento) return null;

  if (segmento.needsThreshold) {
    return (
      <div className="flex flex-col gap-1.5">
        <span className="text-[13px] font-semibold text-ink">¿Desde cuántas inasistencias?</span>
        <div className="flex items-center gap-2.5 text-[13.5px] text-ink-soft">
          <span>Alumnos con</span>
          <Input
            type="number"
            min={1}
            max={40}
            value={spec.threshold ?? 3}
            onChange={(e) => onChange({ ...spec, threshold: Number(e.target.value) || 1 })}
            className="w-20 text-center"
            aria-label="Cantidad mínima de inasistencias"
          />
          <span>o más inasistencias</span>
        </div>
      </div>
    );
  }

  if (!segmento.needsInstances) return null;

  const instancias = spec.instances ?? [];
  const total = segmento.instanceCount ?? 0;

  function alternar(numero) {
    const siguiente = instancias.includes(numero)
      ? instancias.filter((i) => i !== numero)
      : [...instancias, numero].sort((a, b) => a - b);
    onChange({ ...spec, instances: siguiente });
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-1.5">
        <span className="text-[13px] font-semibold text-ink" id="et-instancias">
          ¿En cuáles?
        </span>
        <div className="flex flex-wrap gap-2" role="group" aria-labelledby="et-instancias">
          {Array.from({ length: total }, (_, i) => i + 1).map((numero) => (
            <Chip key={numero} activo={instancias.includes(numero)} onClick={() => alternar(numero)}>
              {nombreDeInstancia(segmento.key, numero)}
            </Chip>
          ))}
        </div>
      </div>

      <div className="flex flex-col gap-1.5">
        <span className="text-[13px] font-semibold text-ink" id="et-cuantificador">
          ¿Tiene que cumplirse en…?
        </span>
        <div className="flex flex-wrap gap-2" role="radiogroup" aria-labelledby="et-cuantificador">
          <Chip rol="radio" activo={(spec.quantifier ?? 'ANY') === 'ANY'} onClick={() => onChange({ ...spec, quantifier: 'ANY' })}>
            alguno de ellos
          </Chip>
          <Chip rol="radio" activo={spec.quantifier === 'ALL'} onClick={() => onChange({ ...spec, quantifier: 'ALL' })}>
            todos ellos
          </Chip>
        </div>
      </div>
    </div>
  );
}
